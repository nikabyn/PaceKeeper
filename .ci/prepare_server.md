Steps which are required for a GitLab Runner to Setup the CI with Android Emulator

## Setup SDK

As gitlab-runner user execute:

```bash
export ANDROID_SDK_ROOT=/opt/android-sdk
export PATH=$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:$PATH

yes | sdkmanager --licenses
sdkmanager "platform-tools" "emulator" "system-images;android-28;google_apis;x86" "system-images;android-35;google_apis;x86_64"
```

## GitLab Runner Configuration

As root set the config to mount kvm.

```toml
concurrent = 1
check_interval = 0
connection_max_age = "15m0s"
shutdown_timeout = 0

[session_server]
session_timeout = 1800

[[runners]]
name = "pacing-app-android"
url = "https://gitlab.dit.htwk-leipzig.de"
id = 454
token = "<token>"
token_obtained_at = <timestamp>
token_expires_at = <timestamp>
executor = "docker"
tags = ["dind", "docker", "kvm"]
run_untagged = false
[runners.cache]
MaxUploadedArchiveSize = 0
[runners.cache.s3]
[runners.cache.gcs]
[runners.cache.azure]
[runners.docker]
tls_verify = false
image = "docker:latest"
disable_entrypoint_overwrite = false
oom_kill_disable = false
disable_cache = false
privileged = true
devices = ["/dev/kvm"]
volumes = ["/cache", "/dev/kvm:/dev/kvm", "/opt/android-sdk:/opt/android", "/home/gitlab-runner/.android:/root/.android"]
shm_size = 0
network_mtu = 0
```