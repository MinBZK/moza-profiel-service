#!/bin/bash -eu

# Build the project
# We use -DskipTests because we only want to compile everything, not run tests yet.
./mvnw package -DskipTests -B

# Copy dependencies to $OUT/lib
mkdir -p $OUT/lib
./mvnw dependency:copy-dependencies -DoutputDirectory=$OUT/lib -B

# Copy the project's own jars
cp target/*.jar $OUT/lib/

# Copy classes and test-classes
cp -r target/classes $OUT/classes
cp -r target/test-classes $OUT/test-classes

# Find all @FuzzTest methods in EndpointFuzzTest
# This assumes the file structure is consistent
METHODS=$(grep "@FuzzTest" src/test/java/nl/rijksoverheid/moz/fuzzing/EndpointFuzzTest.java -A 1 | grep "public void" | sed 's/.*void \([^( ]*\).*/\1/')

for method in $METHODS; do
  fuzzer_name="EndpointFuzzTest_$method"
  
  echo "Creating wrapper for $fuzzer_name"
  
  # Create a wrapper script
  # We use /out/ because that's where $OUT is mapped in the runner container
  cat << EOF > $OUT/$fuzzer_name
#!/bin/bash
CLASSPATH="/out/test-classes:/out/classes:/out/lib/*"
jazzer \\
  --cp=\$CLASSPATH \\
  --target_class=com.code_intelligence.jazzer.junit.JazzerJUnitRunner \\
  --target_args=nl.rijksoverheid.moz.fuzzing.EndpointFuzzTest::$method \\
  \$@
EOF
  chmod +x $OUT/$fuzzer_name
done
