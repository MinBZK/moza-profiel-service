FROM gcr.io/oss-fuzz-base/base-builder-jvm:v1
COPY . $SRC/moza-profiel-service
WORKDIR $SRC/moza-profiel-service
RUN echo "PWD:" && pwd && \
    echo "FILES:" && ls -la \

RUN cp build.sh $SRC/build.sh