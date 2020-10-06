#!/bin/bash
#Ref: https://developer.android.com/ndk/guides/standalone_toolchain
#Ref: https://developer.android.com/ndk/guides/other_build_systems
# FFMpeg 4.0.x compile x86/x86_64 without --disable-asm will failed by text relocations when loading library on device API-23 or above
# Ref https://android.googlesource.com/platform/bionic/+/master/android-changes-for-ndk-developers.md#Text-Relocations-Enforced-for-API-level-23

PWD=$(pwd)

TARGET=$1
if [[ -z $TARGET ]]; then
    echo "Usage: $0 armeabi-v7a|arm64-v8a|x86|x86_64"
    exit -1
fi

NDK_PATH=$ANDROID_NDK_HOME
if [[ -z $NDK_PATH ]]; then
    echo "Please specify ANDROID_NDK_HOME"
    exit -1
fi

# linux-x86 darwin-x86_64
NDK_HOST_TAG=$(echo "$(uname -s)-$(uname -m)" | tr "[A-Z]" "[a-z]")
if [ ! -d ${NDK_PATH}/toolchains/arm-linux-androideabi-4.9/prebuilt/${NDK_ARCH} ]; then
    NDK_HOST_TAG=$(echo "$(uname -s)-x86" | tr "[A-Z]" "[a-z]")
fi

# TARGET_ARCH: 'arm', 'arm64', 'x86', 'x86_64'
# TARGET_ARCH_ABI: 'armeabi-v7a' 'arm64-v8a' 'x86' 'x86_64'

# Note: For 32-bit ARM, the compiler is prefixed with armv7a-linux-androideabi
# but the binutils tools are prefixed with arm-linux-androideabi.
# For other architectures, the prefixes are the same for all tools.
case $TARGET in
armeabi-v7a)
    FFMPEG_ARCH=armv7-a # Actually equals as "arm", will include all the arm-eabi
    FFMPEG_CPU=cortex-a8 # Limit the minimum require is cortex-a8, optimize for armv7-a or newer cpu
    FFMPEG_OPTIONS="--enable-thumb --disable-fast-unaligned --disable-neon" # Disable fast-unaligned to avoid sigbus
    SYSROOT_ARCH="arch-arm"
    ANDROID_API=16
    TARGET_TRIPLE="arm-linux-androideabi"
    TARGET_TRIPLE_COMPILER="armv7a-linux-androideabi"
    EXTRA_CFLAGS="-march=armv7-a -mfloat-abi=softfp -mfpu=vfpv3-d16 -mtune=cortex-a8"
    #EXTRA_LDFLAGS="-march=armv7-a --fix-cortex-a8"
    #EXTRA_LDFLAGS="-march=armv7-a -Wl,--fix-cortex-a8" # Use clang to link should use '-Wl,--fix-cortex-a8' to pass for ld correctly
    EXTRA_LDFLAGS="-march=armv7-a" # With lld, remove the fix-cortex-*
    ;;
arm64-v8a) # AArch-64
    FFMPEG_ARCH=aarch64 #arm64
    FFMPEG_CPU=cortex-a53
    FFMPEG_OPTIONS="--enable-thumb --disable-fast-unaligned" # Disable fast-unaligned to avoid sigbus
    SYSROOT_ARCH="arch-arm64"
    ANDROID_API=21
    TARGET_TRIPLE="aarch64-linux-android"
    TARGET_TRIPLE_COMPILER="${TARGET_TRIPLE}"
    EXTRA_CFLAGS="-march=armv8-a"
    #EXTRA_LDFLAGS="--fix-cortex-a53-843419 --fix-cortex-a53-835769"
    #EXTRA_LDFLAGS="-Wl,--fix-cortex-a53-843419 -Wl,--fix-cortex-a53-835769" # Use clang to link should use '-Wl,--fix-cortex-a53-xxx' to pass for ld correctly
    EXTRA_LDFLAGS="" # With lld, remove the fix-cortex-*
    ;;
x86)
    FFMPEG_ARCH=x86
    FFMPEG_CPU=atom
    FFMPEG_OPTIONS="--disable-amd3dnow --disable-sse4 --disable-asm" # SSE4 SSE4.2 only avaliable for X86-64
    SYSROOT_ARCH="arch-x86"
    ANDROID_API=16
    TARGET_TRIPLE="i686-linux-android"
    TARGET_TRIPLE_COMPILER="${TARGET_TRIPLE}"
    EXTRA_CFLAGS="-march=i686 -mtune=intel -mstackrealign -mfpmath=sse -ffast-math -msse3 -mssse3" # Follow NDK internal, gcc4.8+ need mtune=intel instead mtune=atom
    ;;
x86_64)
    FFMPEG_ARCH=x86_64
    FFMPEG_CPU=atom
    FFMPEG_OPTIONS="--disable-amd3dnow --disable-asm"
    SYSROOT_ARCH="arch-x86_64"
    ANDROID_API=21
    TARGET_TRIPLE="x86_64-linux-android"
    TARGET_TRIPLE_COMPILER="${TARGET_TRIPLE}"
    EXTRA_CFLAGS="-march=x86_64 -mtune=intel -mstackrealign -mfpmath=sse -ffast-math -fomit-frame-pointer" # Follow NDK internal, gcc4.8+ need mtune=intel instead mtune=atom
    EXTRA_LDFLAGS="-L${NDK_PATH}/platforms/android-${ANDROID_API}/${SYSROOT_ARCH}/usr/lib64"
    # enable asm will got error: libswscale/x86/rgb2rgb.o: requires dynamic R_X86_64_PC32 reloc against 'ff_w1111' which may overflow at runtime; recompile with -fPIC
    # enable asm will got error: libswscale/x86/swscale.o: requires dynamic R_X86_64_PC32 reloc against 'ff_M24A' which may overflow at runtime; recompile with -fPIC
    ;;
*)
    echo "Invalid TARGET: $TARGET"
    ;;
esac

SYSROOT="${NDK_PATH}/platforms/android-${ANDROID_API}/${SYSROOT_ARCH}"
LLVM_PREFIX="${NDK_PATH}/toolchains/llvm/prebuilt/${NDK_HOST_TAG}"
#export AR="${LLVM_PREFIX}/bin/${TARGET_TRIPLE}-ar"
#export AS="${LLVM_PREFIX}/bin/${TARGET_TRIPLE}-as" # Use '-as' for AS, https://developer.android.google.cn/ndk/guides/standalone_toolchain
#export AS="${LLVM_PREFIX}/bin/${TARGET_TRIPLE_COMPILER}${ANDROID_API}-clang" # Use '-clang' for AS, https://developer.android.google.cn/ndk/guides/other_build_systems
export CC="${LLVM_PREFIX}/bin/${TARGET_TRIPLE_COMPILER}${ANDROID_API}-clang"
#export CPP="${LLVM_PREFIX}/bin/${TARGET_TRIPLE_COMPILER}${ANDROID_API}-clang -E"
export CXX="${LLVM_PREFIX}/bin/${TARGET_TRIPLE_COMPILER}${ANDROID_API}-clang++"
#export LD="${LLVM_PREFIX}/bin/${TARGET_TRIPLE}-ld" # armeabi-v7a, x86, x86_64 use ld.gold, aarch64-v8a use ld.bfd
#export LD="${LLVM_PREFIX}/bin/${TARGET_TRIPLE}-ld.bfd"
#export LD="${LLVM_PREFIX}/bin/${TARGET_TRIPLE}-ld.gold"
#export LD="${LLVM_PREFIX}/bin/ld.lld"
#export NM="${LLVM_PREFIX}/bin/${TARGET_TRIPLE}-nm"
#export RANLIB="${LLVM_PREFIX}/bin/${TARGET_TRIPLE}-ranlib"
#export STRIP="${LLVM_PREFIX}/bin/${TARGET_TRIPLE}-strip"
#export OBJDUMP="${LLVM_PREFIX}/bin/${TARGET_TRIPLE}-objdump"
export CFLAGS="-D__ANDROID__ -D__ANDROID_API__=${ANDROID_API} -I${NDK_PATH}/sysroot/usr/include -I${NDK_PATH}/sysroot/usr/include/${TARGET_TRIPLE} ${EXTRA_CFLAGS}"
export CXXFLAGS="${EXTRA_CXXFLAGS}"
export LDFLAGS="${EXTRA_LDFLAGS}"
export LIBS=""

echo "AR: ${AR}"
echo "AS: ${AS}"
echo "CC: ${CC}"
echo "CPP: ${CPP}"
echo "CXX: ${CXX}"
echo "LD: ${LD}"
echo "NM: ${NM}"
echo "RANLIB: ${RANLIB}"
echo "STRIP: ${STRIP}"
echo "OBJDUMP: ${OBJDUMP}"
echo "CFLAGS: ${CFLAGS}"
echo "CXXFLAGS: ${CXXFLAGS}"
echo "LDFLAGS: ${LDFLAGS}"
echo "LIBS: ${LIBS}"

./configure \
    --sysroot="$SYSROOT" \
    --enable-cross-compile \
    --prefix="$PWD/../prebuilt/$TARGET" \
    --cross-prefix="$LLVM_PREFIX/bin/${TARGET_TRIPLE}-" \
    --arch=$FFMPEG_ARCH \
    --cpu=$FFMPEG_CPU \
    --cc=$CC \
    --cxx=$CXX \
    --ld=$LD \
    --nm=$NM \
    --ar=$AR \
    --as=$AS \
    --strip=$STRIP \
    --ranlib=$RANLIB \
    --target-os=android \
    --disable-everything \
    --disable-doc \
    --disable-programs \
    --disable-avdevice \
    --disable-decoders \
    --disable-encoders \
    --disable-swresample \
    --disable-swscale \
    --disable-muxers \
    --disable-demuxers \
    --disable-bsfs \
    --disable-indevs \
    --disable-outdevs \
    --disable-filters \
    --disable-postproc \
    --disable-avfilter \
    --disable-shared \
    --enable-static \
    --enable-avformat \
    --enable-protocol=rtmp \
    --enable-parser=h264,aac \
    --enable-muxer=flv \
    --enable-pic \
    $FFMPEG_OPTIONS

#--enable-jni \

