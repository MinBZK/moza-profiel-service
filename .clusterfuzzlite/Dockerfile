FROM gcr.io/oss-fuzz-base/base-builder-jvm:v1
COPY . $SRC/moza-profiel-service
WORKDIR $SRC/moza-profiel-service
COPY ./.clusterfuzzlite/build.sh $SRC/