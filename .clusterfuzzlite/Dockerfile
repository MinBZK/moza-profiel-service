FROM gcr.io/oss-fuzz-base/base-builder:v1
RUN apt-get update && apt-get install -y openjdk-21-jdk
COPY . $SRC/moza-profiel-service
WORKDIR moza-profiel-service
RUN chmod +x mvnw
COPY .clusterfuzzlite/build.sh $SRC/
