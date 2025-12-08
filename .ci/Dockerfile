FROM debian:bookworm-slim

ARG API_LEVEL=28
ARG BUILD_TOOLS=34.0.0
ARG CMDLINE_TOOLS_PKG=commandlinetools-linux-11076708_latest.zip

ENV ANDROID_API_LEVEL=$API_LEVEL
ENV ANDROID_HOME=/opt/android
ENV ANDROID_SDK_ROOT=$ANDROID_HOME
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH="$JAVA_HOME/bin:$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator"

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
    bash ca-certificates git unzip wget curl \
    lib32stdc++6 lib32z1 libc6-i386 \
    libglu1-mesa libpulse0 libxi6 libxrender1 libxrandr2 libxcomposite1 libxcursor1 libxinerama1 \
    libnss3 libx11-6 libxcb1 libdbus-1-3 libgl1 libgl1-mesa-dri \
    openjdk-17-jdk-headless kmod udev procps \
    iputils-ping net-tools \
 && apt-get clean \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /tmp

RUN wget -qO cmdline-tools.zip "https://dl.google.com/android/repository/${CMDLINE_TOOLS_PKG}" \
 && mkdir -p "$ANDROID_HOME/cmdline-tools" \
 && unzip -q cmdline-tools.zip -d "$ANDROID_HOME/cmdline-tools" \
 && mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest" \
 && rm -rf cmdline-tools.zip

RUN yes | sdkmanager --licenses >/dev/null \
 && sdkmanager "platform-tools" "emulator" "build-tools;${BUILD_TOOLS}" "platforms;android-${ANDROID_API_LEVEL}" \
 && sdkmanager "system-images;android-${ANDROID_API_LEVEL};google_apis;x86_64" \
 && rm -rf /tmp/*

ENV BASE_AVD_HOME=/opt/avd-template
RUN mkdir -p "$BASE_AVD_HOME" \
 && echo "no" | avdmanager create avd \
      -n "base-avd" \
      -k "system-images;android-${ANDROID_API_LEVEL};google_apis;x86_64" \
      -p "$BASE_AVD_HOME/base-avd.avd" \
      --device "pixel" --force \
 && echo "hw.cpu.ncore=2" >> "$BASE_AVD_HOME/base-avd.avd/config.ini" \
 && echo "hw.gpu.enabled=yes" >> "$BASE_AVD_HOME/base-avd.avd/config.ini" \
 && echo "hw.gpu.mode=swiftshader_indirect" >> "$BASE_AVD_HOME/base-avd.avd/config.ini" \
 && echo "hw.ramSize=2048" >> "$BASE_AVD_HOME/base-avd.avd/config.ini" \
 && echo "vm.heapSize=512" >> "$BASE_AVD_HOME/base-avd.avd/config.ini" \
 && echo "fastboot.forceColdBoot=yes" >> "$BASE_AVD_HOME/base-avd.avd/config.ini"

COPY start-emulator.sh /opt/start-emulator.sh
COPY stop-emulator.sh  /opt/stop-emulator.sh

RUN sed -i 's/\r$//' /opt/start-emulator.sh /opt/stop-emulator.sh \
 && chmod +x /opt/start-emulator.sh /opt/stop-emulator.sh

WORKDIR /workspace

CMD ["bash"]