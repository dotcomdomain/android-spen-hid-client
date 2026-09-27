#!/data/data/com.termux/files/usr/bin/sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
sdk_dir=${ANDROID_HOME:-/data/data/com.termux/files/home/android-sdk}
native_dir="$project_dir/app/build/termux-jniLibs/arm64-v8a"
mkdir -p "$native_dir"
clang++ -std=c++17 -shared -fPIC \
    "$project_dir/app/src/main/cpp/uhid.cpp" -llog \
    -o "$native_dir/libusb_hid_client.so"

cd "$project_dir"
target_task=${1:-:app:assembleDebug}
ANDROID_HOME="$sdk_dir" ./gradlew "$target_task" \
    -PtermuxBuild \
    -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 \
    --no-daemon
