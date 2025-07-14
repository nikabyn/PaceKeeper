FROM debian:bookworm-slim

ARG ANDROID_API=28
ARG BUILD_TOOLS=28.0.0
ARG CMDLINE_TOOLS_PKG=commandlinetools-linux-13114758_latest

ENV ANDROID_HOME=/opt/android
ENV ANDROID_SDK_ROOT=$ANDROID_HOME
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator
ENV DEBIAN_FRONTEND=noninteractive

COPY start-avds.sh /opt/
COPY stop-avds.sh /opt/

WORKDIR /tmp

RUN apt-get update; \
    apt-get install -y --no-install-recommends \
        bash \
        ca-certificates \
        git \
        lib32stdc++6 \
        lib32z1 \
        libc6-i386 \
        libglu1-mesa \
        libpulse0 \
        openjdk-17-jdk-headless \
        bridge-utils \
        xauth \
        xvfb \
        libxi6 \
        libxrender1 \
        mesa-utils \
        unzip \
        wget; \
    rm -rf /var/lib/apt/lists/*

RUN wget -qO cmdline-tools.zip "https://dl.google.com/android/repository/${CMDLINE_TOOLS_PKG}.zip"; \
    mkdir -p "$ANDROID_HOME/cmdline-tools/latest"; \
    unzip -q cmdline-tools.zip -d "$ANDROID_HOME/cmdline-tools/temp"; \
    mv "$ANDROID_HOME/cmdline-tools/temp/cmdline-tools/"* "$ANDROID_HOME/cmdline-tools/latest/"; \
    rm -rf cmdline-tools.zip "$ANDROID_HOME/cmdline-tools/temp"

RUN yes | sdkmanager --licenses && \
    yes | sdkmanager \
        "platform-tools" \
        "build-tools;${BUILD_TOOLS}" \
        "platforms;android-${ANDROID_API}" \
        "extras;android;m2repository" \
        "extras;google;m2repository" && \
    yes | sdkmanager --update

RUN rm -rf /tmp/*
RUN chmod +x /opt/start-avds.sh

CMD ["bash"]