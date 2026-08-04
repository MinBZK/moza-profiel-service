FROM gcr.io/oss-fuzz-base/base-builder-jvm:v1@sha256:73e9f2aaddb0a9ca97b137c1e7d8f2142203be6a5767be48a56888491edaf310

# De service compileert met --release 25, dus we hebben JDK 25 nodig
# (base-builder-jvm draait op Ubuntu 20.04 en levert JDK 17 mee).
# Ubuntu 20.04 heeft geen openjdk-25 pakket, daarom installeren we Temurin 25
# uit de tarball. Versie + checksum staan gepind zodat de build reproduceerbaar is.
ARG JDK_URL=https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.3%2B9/OpenJDK25U-jdk_x64_linux_hotspot_25.0.3_9.tar.gz
ARG JDK_SHA256=69264a7a211bf5029830d07bc3370f879769d62ebc5b5488e90c9343a2da0e1f

RUN apt-get update && apt-get install -y rsync curl \
    && curl -fsSL -o /tmp/jdk25.tar.gz "$JDK_URL" \
    && echo "$JDK_SHA256  /tmp/jdk25.tar.gz" | sha256sum -c - \
    && mkdir -p /usr/lib/jvm/java-25-temurin \
    && tar -xzf /tmp/jdk25.tar.gz -C /usr/lib/jvm/java-25-temurin --strip-components=1 \
    && rm /tmp/jdk25.tar.gz

ENV JAVA_HOME=/usr/lib/jvm/java-25-temurin
ENV PATH=$JAVA_HOME/bin:$PATH

COPY . $SRC/moza-profiel-service
WORKDIR $SRC/moza-profiel-service
RUN chmod +x mvnw
COPY .clusterfuzzlite/build.sh $SRC/
