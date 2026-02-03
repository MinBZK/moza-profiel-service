FROM gcr.io/oss-fuzz-base/base-builder-jvm:v1

# Install any additional dependencies if needed
# RUN apt-get update && apt-get install -y ...

COPY . $SRC/moza-profiel-service
WORKDIR $SRC/moza-profiel-service
COPY .clusterfuzzlite/build.sh $SRC/build.sh
