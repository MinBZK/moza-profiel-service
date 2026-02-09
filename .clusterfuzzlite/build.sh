#!/bin/bash -eu

# Build the project (compiles everything including tests, but doesn't run them)
./mvnw package -DskipTests -Djacoco.skip=true -B

# Copy all dependencies to $OUT/lib
mkdir -p $OUT/lib
./mvnw dependency:copy-dependencies -DoutputDirectory=$OUT/lib -B

# Copy compiled application and test classes
cp -r target/classes $OUT/classes
cp -r target/test-classes $OUT/test-classes

# Copy JDK 21 runtime to $OUT so the runner can execute Java 21 bytecode.
# The project requires Java 21 (dependency compiled with class version 65).
# We copy lib/ (libjvm.so + modules) and conf/ (java.security) so that
# java.home derived from libjvm.so's location resolves correctly.
mkdir -p $OUT/jdk
cp -r "$JAVA_HOME/lib"  "$OUT/jdk/"
cp -r "$JAVA_HOME/conf" "$OUT/jdk/"

# Verify the critical java.security file was copied
if [ ! -f "$OUT/jdk/conf/security/java.security" ]; then
  echo "ERROR: java.security not found at $OUT/jdk/conf/security/java.security"
  ls -laR "$OUT/jdk/conf/" 2>&1 || true
  exit 1
fi

# Create a wrapper script for every standalone fuzzer
# (classes that define the static fuzzerTestOneInput method expected by jazzer_driver)
for fuzzer in $(grep -rl "fuzzerTestOneInput" src/test/java/ || true); do
  class_name=$(echo "$fuzzer" | sed 's|src/test/java/||;s|\.java$||;s|/|.|g')
  simple_name=$(basename -s .java "$fuzzer")

  echo "Creating fuzzer wrapper: $simple_name -> $class_name"

  # Quoted heredoc: no variable expansion; TARGET_CLASS is replaced by sed below
  # The LLVMFuzzerTestOneInput comment is required for CFL's fuzz target detection
  cat > "$OUT/$simple_name" << 'WRAPPER_EOF'
#!/bin/bash
# LLVMFuzzerTestOneInput for jvm
this_dir=$(dirname "$0")

# Point JAVA_HOME and LD_LIBRARY_PATH at the bundled JDK 21 so that
# jazzer_driver loads *our* libjvm.so (not the runner's JDK 17).
export JAVA_HOME="$this_dir/jdk"
export LD_LIBRARY_PATH="$this_dir/jdk/lib/server:$this_dir/jdk/lib:${LD_LIBRARY_PATH:-}"

# Build classpath from compiled classes and all dependency jars
CP="$this_dir/test-classes:$this_dir/classes"
for jar in "$this_dir"/lib/*.jar; do
  CP="$CP:$jar"
done

"$this_dir/jazzer_driver" \
  --agent_path="$this_dir/jazzer_agent_deploy.jar" \
  --cp="$CP" \
  --target_class=TARGET_CLASS_PLACEHOLDER \
  --jvm_args="-Xmx2048m" \
  "$@"
WRAPPER_EOF

  sed -i "s|TARGET_CLASS_PLACEHOLDER|$class_name|" "$OUT/$simple_name"
  chmod +x "$OUT/$simple_name"
done
