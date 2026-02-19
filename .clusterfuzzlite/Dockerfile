FROM gcr.io/oss-fuzz-base/base-builder-jvm:v1@sha256:73e9f2aaddb0a9ca97b137c1e7d8f2142203be6a5767be48a56888491edaf310

# The logboekdataverwerking-wrapper dependency is compiled with Java 21,
# so we need JDK 21 for compilation (base-builder-jvm ships JDK 17).
RUN apt-get update && apt-get install -y openjdk-21-jdk rsync

ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV PATH=$JAVA_HOME/bin:$PATH

COPY . $SRC/moza-profiel-service
WORKDIR $SRC/moza-profiel-service
RUN chmod +x mvnw
COPY .clusterfuzzlite/build.sh $SRC/
