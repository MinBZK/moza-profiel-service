#!/bin/bash -eu

# Build the project with H2 database for fuzzing.
# quarkus.datasource.db-kind is a build-time property — must be set during compilation.
./mvnw package -DskipTests -Djacoco.skip=true \
  -Dquarkus.datasource.db-kind=h2 \
  -Dquarkus.datasource.jdbc.url=jdbc:h2:mem:fuzztest \
  -Dlogboekdataverwerking.enabled=false \
  -B

# Copy all dependencies to $OUT/lib
mkdir -p $OUT/lib
./mvnw dependency:copy-dependencies -DoutputDirectory=$OUT/lib -B

# Copy compiled application and test classes
cp -r target/classes $OUT/classes
cp -r target/test-classes $OUT/test-classes

# Copy the full quarkus-app directory so the EndpointFuzzer wrapper can start
# Quarkus as a subprocess (java -jar quarkus-run.jar). H2 is baked in at build time.
cp -r target/quarkus-app $OUT/quarkus-app

# Bundle the ENTIRE JDK 21 runtime to $OUT so the runner can execute Java 21 bytecode.
# Uses rsync -aL to dereference symlinks (critical for JDK directory structure).
# Pattern taken from the oss-fuzz tomcat project.
mkdir -p "$OUT/open-jdk-21"
rsync -aL --exclude='*.zip' "$JAVA_HOME/" "$OUT/open-jdk-21/"

# Create a wrapper script for every standalone fuzzer
# (classes that define the static fuzzerTestOneInput method expected by jazzer_driver)
for fuzzer in $(grep -rl "fuzzerTestOneInput" src/test/java/ || true); do
  class_name=$(echo "$fuzzer" | sed 's|src/test/java/||;s|\.java$||;s|/|.|g')
  simple_name=$(basename -s .java "$fuzzer")

  echo "Creating fuzzer wrapper: $simple_name -> $class_name"

  if [ "$simple_name" = "EndpointFuzzer" ]; then
    # ── Special wrapper for EndpointFuzzer ──
    # Starts Quarkus as a subprocess (java -jar quarkus-run.jar) before
    # launching jazzer_driver. Quarkus was built with H2 at compile time.
    cat > "$OUT/$simple_name" << 'WRAPPER_EOF'
#!/bin/bash
# LLVMFuzzerTestOneInput for jvm
this_dir=$(dirname "$0")

if [[ "$@" =~ (^| )-runs=[0-9]+($| ) ]]; then
  mem_settings='-Xmx1900m:-Xss900k'
else
  mem_settings='-Xmx2048m:-Xss1024k'
fi

# Start Quarkus as a background process with H2 in-memory database.
JAVA_HOME="$this_dir/open-jdk-21" \
LD_LIBRARY_PATH="$this_dir/open-jdk-21/lib/server" \
"$this_dir/open-jdk-21/bin/java" \
  -Dquarkus.http.port=8081 \
  -Dquarkus.log.level=WARN \
  -Dquarkus.rest-client.basisprofiel-api.url=http://localhost:9999 \
  -Dquarkus.rest-client.email-api.url=http://localhost:9999 \
  -Dlogboekdataverwerking.enabled=false \
  -jar "$this_dir/quarkus-app/quarkus-run.jar" &
QUARKUS_PID=$!
trap "kill $QUARKUS_PID 2>/dev/null || true" EXIT

# Wait for Quarkus to accept connections (up to 20 seconds)
for i in $(seq 1 80); do
  if curl -sf -o /dev/null http://localhost:8081/ 2>/dev/null; then
    break
  fi
  sleep 0.25
done

# Build classpath from compiled classes and all dependency jars
CP="$this_dir/test-classes:$this_dir/classes"
for jar in "$this_dir"/lib/*.jar; do
  CP="$CP:$jar"
done

JAVA_HOME="$this_dir/open-jdk-21" \
LD_LIBRARY_PATH="$this_dir/open-jdk-21/lib/server":"$this_dir" \
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
# the bundled JDK 21 libjvm.so (not the runner's JDK 17).
# Pattern taken from the oss-fuzz tomcat project.
JAVA_HOME="$this_dir/open-jdk-21" \
LD_LIBRARY_PATH="$this_dir/open-jdk-21/lib/server":"$this_dir" \
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
