#!/bin/bash
# Builds libalumnn.so (JNI bridge) against prebuilt MNN 3.6.1 Android libs.
# Needs: NDK (r27), MNN headers in $MNN_INC (include/MNN + llm/llm.hpp), prebuilt libs in $MNN_LIBS/<abi>/
set -e
NDK=${NDK:-/home/user/.cache/ndk/android-ndk-r27c}
MNN_INC=${MNN_INC:-/home/user/.cache/work/mnninc}
MNN_LIBS=${MNN_LIBS:-/home/user/.cache/work/mnnlibs/mnn_3.6.1_android_armv7_armv8_cpu_opencl_vulkan}
OUT=${OUT:-$(dirname "$0")/../app/src/main/jniLibs}
TC=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin
for ABI in arm64-v8a armeabi-v7a; do
  if [ "$ABI" = arm64-v8a ]; then CXX=$TC/aarch64-linux-android24-clang++; else CXX=$TC/armv7a-linux-androideabi24-clang++; fi
  mkdir -p "$OUT/$ABI"
  $CXX -std=c++17 -O2 -fPIC -shared -fvisibility=hidden -DMNN_USE_SHARED \
     -I"$MNN_INC" "$(dirname "$0")/alu_mnn_jni.cpp" \
     -L"$MNN_LIBS/$ABI" -lllm -lMNN -lMNN_Express -llog -landroid \
     -Wl,-z,max-page-size=16384 -Wl,--no-undefined \
     -o "$OUT/$ABI/libalumnn.so"
  for L in libMNN.so libMNN_Express.so libMNN_CL.so libMNN_Vulkan.so libMNNOpenCV.so libMNNAudio.so libllm.so libc++_shared.so; do [ "$MNN_LIBS/$ABI/$L" -ef "$OUT/$ABI/$L" ] || cp "$MNN_LIBS/$ABI/$L" "$OUT/$ABI/"; done
  rm -f "$OUT/$ABI/libaluai.so"
  echo "built $ABI"; ls -la "$OUT/$ABI"
done
