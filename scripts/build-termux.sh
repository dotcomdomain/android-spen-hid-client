#!/data/data/com.termux/files/usr/bin/sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
sdk_dir=${ANDROID_HOME:-/data/data/com.termux/files/home/android-sdk}
native_dir="$project_dir/app/build/termux-jniLibs/arm64-v8a"
whisper_build="$project_dir/app/build/termux-whisper"
mkdir -p "$native_dir"
clang++ -std=c++17 -shared -fPIC \
    "$project_dir/app/src/main/cpp/uhid.cpp" -llog \
    -o "$native_dir/libusb_hid_client.so"

cmake -S "$project_dir/third_party/whisper.cpp" -B "$whisper_build" \
    -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF \
    -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
    -DWHISPER_BUILD_TESTS=OFF -DWHISPER_BUILD_EXAMPLES=OFF -DWHISPER_BUILD_SERVER=OFF \
    -DGGML_NATIVE=OFF -DGGML_OPENMP=OFF -DGGML_VULKAN=OFF -DGGML_CPU_KLEIDIAI=OFF
cmake --build "$whisper_build" -j 4
clang++ -std=c++17 -O3 -shared -fPIC \
    -I"$project_dir/third_party/whisper.cpp/include" \
    -I"$project_dir/third_party/whisper.cpp/ggml/include" \
    "$project_dir/app/src/main/cpp/dictation.cpp" \
    "$whisper_build/src/libwhisper.a" \
    "$whisper_build/ggml/src/libggml.a" \
    "$whisper_build/ggml/src/libggml-cpu.a" \
    "$whisper_build/ggml/src/libggml-base.a" \
    -llog -ldl -lm -pthread -Wl,--build-id=none \
    -o "$native_dir/libdictation.so"
cp /data/data/com.termux/files/usr/lib/libc++_shared.so "$native_dir/libc++_shared.so"

cd "$project_dir"
if [ "$#" -eq 0 ]; then set -- :app:assembleDebug; fi
ANDROID_HOME="$sdk_dir" ./gradlew "$@" \
    -PtermuxBuild \
    -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 \
    --no-daemon
