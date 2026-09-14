#!/bin/bash -eu

# Build the project for fuzzing. quarkus.datasource.db-kind is a build-time
# property and the application already defaults to postgresql, so the fuzz
# target hits the same dialect and constraints as production.
./mvnw package -DskipTests -Djacoco.skip=true \
  -Dlogboekdataverwerking.enabled=false \
  -B

# Copy all dependencies to $OUT/lib. The PostgreSQL bundles for the other
# platforms can never run in this container; linux-amd64 stays, because the
# server is unpacked from it below.
mkdir -p $OUT/lib
./mvnw dependency:copy-dependencies -DoutputDirectory=$OUT/lib -B \
  -DexcludeArtifactIds=embedded-postgres-binaries-windows-amd64,embedded-postgres-binaries-darwin-amd64,embedded-postgres-binaries-darwin-arm64v8,embedded-postgres-binaries-linux-arm64v8,embedded-postgres-binaries-linux-amd64-alpine

# Copy compiled application and test classes
cp -r target/classes $OUT/classes
cp -r target/test-classes $OUT/test-classes

# Copy the full quarkus-app directory so the EndpointFuzzer wrapper can start
# Quarkus as a subprocess (java -jar quarkus-run.jar).
cp -r target/quarkus-app $OUT/quarkus-app

# Unpack the PostgreSQL server that the EndpointFuzzer wrapper starts before
# Quarkus. The binaries arrive in $OUT/lib as a transitive dependency of
# embedded-postgres, so excluding the linux-amd64 platform in pom.xml would
# break fuzzing as well as the tests.
mkdir -p "$OUT/postgres"
PG_JAR=$(ls "$OUT"/lib/embedded-postgres-binaries-linux-amd64-*.jar | head -1)
unzip -p "$PG_JAR" postgres-linux-x86_64.txz | tar -xJf - -C "$OUT/postgres"

# Bundle the ENTIRE JDK 25 runtime to $OUT so the runner can execute Java 25 bytecode.
# Uses rsync -aL to dereference symlinks (critical for JDK directory structure).
# Pattern taken from the oss-fuzz tomcat project.
mkdir -p "$OUT/open-jdk-25"
rsync -aL --exclude='*.zip' "$JAVA_HOME/" "$OUT/open-jdk-25/"

# Create a wrapper script for every standalone fuzzer
# (classes that define the static fuzzerTestOneInput method expected by jazzer_driver)
for fuzzer in $(grep -rl "fuzzerTestOneInput" src/test/java/ || true); do
  class_name=$(echo "$fuzzer" | sed 's|src/test/java/||;s|\.java$||;s|/|.|g')
  simple_name=$(basename -s .java "$fuzzer")

  echo "Creating fuzzer wrapper: $simple_name -> $class_name"

  if [ "$simple_name" = "EndpointFuzzer" ]; then
    # ── Special wrapper for EndpointFuzzer ──
    # Starts PostgreSQL and then Quarkus as subprocesses before launching
    # jazzer_driver.
    cat > "$OUT/$simple_name" << 'WRAPPER_EOF'
#!/bin/bash
# LLVMFuzzerTestOneInput for jvm
# Same net as the build script: without it an empty this_dir would turn the rm -rf
# and chown below into root-level operations on the wrong path.
set -eu
# Absolute, because the paths below are handed to a shell running as another user.
this_dir=$(cd "$(dirname "$0")" && pwd)

if [[ "$@" =~ (^| )-runs=[0-9]+($| ) ]]; then
  mem_settings='-Xmx1900m:-Xss900k'
else
  mem_settings='-Xmx2048m:-Xss1024k'
fi

# PostgreSQL has to be up before Quarkus: Flyway migrates at start, and the
# endpoints under fuzz depend on constraints that only the real database has.
PGDIR="$this_dir/postgres"
PGDATA="$this_dir/pgdata"
PGPORT=5433

# initdb and postgres refuse to run as root, which is what the fuzz runner uses.
# The helpers take argv, not a command string: a string is re-parsed by the second
# shell, so any path with a space would be word-split.
if [ "$(id -u)" = "0" ]; then
  id -u pgfuzz >/dev/null 2>&1 || useradd -m pgfuzz
  # The harness runs fuzzers from a mkdtemp directory (mode 0700, owned by root),
  # which pgfuzz cannot traverse. Widen that one directory only: walking up the
  # ancestors would strip 0700 from whatever else happens to be on the path.
  chmod o+rx "$this_dir" 2>/dev/null || true
  if command -v runuser >/dev/null 2>&1; then
    as_postgres() { runuser -u pgfuzz -- "$@"; }
  else
    # su only accepts a command string; %q quotes every argument so the shell it
    # spawns cannot re-split them.
    as_postgres() { su pgfuzz -c "$(printf '%q ' "$@")"; }
  fi
else
  as_postgres() { "$@"; }
fi

stop_postgres() {
  as_postgres "$PGDIR/bin/pg_ctl" -D "$PGDATA" -m immediate stop >/dev/null 2>&1 || true
}

# Quarkus is only running once QUARKUS_PID is set, so guard the expansion under set -u.
cleanup() {
  if [ -n "${QUARKUS_PID:-}" ]; then
    kill "$QUARKUS_PID" 2>/dev/null || true
  fi

  stop_postgres
}

# A run killed before the traps below were armed leaves a postgres that still
# holds PGPORT and this data directory; wiping it underneath that process would
# make every later run fail on a port that is already in use.
if [ -s "$PGDATA/postmaster.pid" ]; then
  echo "EndpointFuzzer: stopping PostgreSQL left behind by an earlier run" >&2
  stop_postgres

  # pg_ctl status, not kill -0 on the pidfile: after a SIGKILL that PID can have
  # been recycled by an unrelated process, and a bare liveness check would then
  # refuse to wipe $PGDATA on every later run.
  if as_postgres "$PGDIR/bin/pg_ctl" -D "$PGDATA" status >/dev/null 2>&1; then
    echo "EndpointFuzzer: leftover PostgreSQL still running after stop; refusing to wipe $PGDATA" >&2
    exit 1
  fi
fi

rm -rf "$PGDATA"
mkdir -p "$PGDATA"

if [ "$(id -u)" = "0" ]; then
  chown -R pgfuzz "$PGDATA" "$PGDIR"
fi

# The bundle ships only initdb, pg_ctl and postgres, so the schema goes into the
# default `postgres` database rather than one created with createdb.
as_postgres "$PGDIR/bin/initdb" -D "$PGDATA" -U profiel --auth=trust -E UTF8 || {
  echo "EndpointFuzzer: initdb failed" >&2
  exit 1
}
# Log inside PGDATA: after the chmod above $this_dir is traversable but not
# writable by pgfuzz, while PGDATA belongs to it.
as_postgres "$PGDIR/bin/pg_ctl" -D "$PGDATA" -o "-p $PGPORT -k /tmp" -l "$PGDATA/postgres.log" -w start || {
  echo "EndpointFuzzer: PostgreSQL failed to start" >&2
  cat "$PGDATA/postgres.log" >&2 || true
  exit 1
}
# Armed here, not after Quarkus starts: everything below can still exit non-zero,
# and postgres is already running. INT/TERM as well as EXIT, because bash skips an
# EXIT trap when the harness kills the run at its timeout.
trap cleanup EXIT
trap 'cleanup; exit 143' INT TERM

# Start Quarkus as a background process against that PostgreSQL.
JAVA_HOME="$this_dir/open-jdk-25" \
LD_LIBRARY_PATH="$this_dir/open-jdk-25/lib/server" \
"$this_dir/open-jdk-25/bin/java" \
  -Dquarkus.http.port=8081 \
  -Dquarkus.log.level=WARN \
  -Dquarkus.datasource.jdbc.url=jdbc:postgresql://localhost:$PGPORT/postgres \
  -Dquarkus.datasource.username=profiel \
  -Dquarkus.datasource.password=profiel \
  -Dquarkus.rest-client.verificatie-service.url=http://localhost:9999 \
  -Dlogboekdataverwerking.enabled=false \
  -Dlogboekdataverwerking.service-name=profiel-service-fuzz \
  -Dnotifynl.emailverificatie.api-key=fuzz \
  -Dnotifynl.emailverificatie.template-id=fuzz \
  -Dnotifynl.emailverificatie.reference=fuzz \
  -jar "$this_dir/quarkus-app/quarkus-run.jar" &
QUARKUS_PID=$!

# Wait for Quarkus to accept connections (up to 60 seconds; Flyway migrates first).
# Fail loudly rather than let jazzer fuzz an application that never came up.
# No -f: nothing is mapped to /, so a 404 already proves the HTTP layer is up.
quarkus_up=
for i in $(seq 1 240); do
  if curl -s -o /dev/null http://localhost:8081/ 2>/dev/null; then
    quarkus_up=1
    break
  fi
  sleep 0.25
done

if [ -z "$quarkus_up" ]; then
  echo "EndpointFuzzer: Quarkus did not come up on port 8081" >&2
  exit 1
fi

# Build classpath from compiled classes and all dependency jars
CP="$this_dir/test-classes:$this_dir/classes"
for jar in "$this_dir"/lib/*.jar; do
  CP="$CP:$jar"
done

JAVA_HOME="$this_dir/open-jdk-25" \
LD_LIBRARY_PATH="$this_dir/open-jdk-25/lib/server":"$this_dir" \
"$this_dir/jazzer_driver" \
  --agent_path="$this_dir/jazzer_agent_deploy.jar" \
  --cp="$CP" \
  --target_class=TARGET_CLASS_PLACEHOLDER \
  --jvm_args="$mem_settings" \
  "$@"
WRAPPER_EOF
  else
    # ── Generic wrapper for in-process fuzzers ──
    cat > "$OUT/$simple_name" << 'WRAPPER_EOF'
#!/bin/bash
# LLVMFuzzerTestOneInput for jvm
this_dir=$(dirname "$0")

if [[ "$@" =~ (^| )-runs=[0-9]+($| ) ]]; then
  mem_settings='-Xmx1900m:-Xss900k'
else
  mem_settings='-Xmx2048m:-Xss1024k'
fi

# Build classpath from compiled classes and all dependency jars
CP="$this_dir/test-classes:$this_dir/classes"
for jar in "$this_dir"/lib/*.jar; do
  CP="$CP:$jar"
done

# Set JAVA_HOME and LD_LIBRARY_PATH inline so jazzer_driver loads
# the bundled JDK 25 libjvm.so (not the runner's JDK 17).
# Pattern taken from the oss-fuzz tomcat project.
JAVA_HOME="$this_dir/open-jdk-25" \
LD_LIBRARY_PATH="$this_dir/open-jdk-25/lib/server":"$this_dir" \
"$this_dir/jazzer_driver" \
  --agent_path="$this_dir/jazzer_agent_deploy.jar" \
  --cp="$CP" \
  --target_class=TARGET_CLASS_PLACEHOLDER \
  --jvm_args="$mem_settings" \
  "$@"
WRAPPER_EOF
  fi

  sed -i "s|TARGET_CLASS_PLACEHOLDER|$class_name|" "$OUT/$simple_name"
  chmod +x "$OUT/$simple_name"
done
