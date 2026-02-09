FROM gcr.io/oss-fuzz-base/base-builder-jvm:v1

# base-builder-jvm ships JDK 17; this project requires JDK 21
RUN apt-get update && apt-get install -y openjdk-21-jdk

ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV JVM_LD_LIBRARY_PATH=$JAVA_HOME/lib/server:$JAVA_HOME/lib
ENV PATH=$JAVA_HOME/bin:$PATH

COPY . $SRC/moza-profiel-service
WORKDIR $SRC/moza-profiel-service
RUN chmod +x mvnw
COPY .clusterfuzzlite/build.sh $SRC/
