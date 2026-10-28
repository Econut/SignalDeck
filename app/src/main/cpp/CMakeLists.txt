# SignalDeck native code: Codec 2 (LGPL-2.1, codec2/COPYING) for M17 voice, and its JNI bridge.
cmake_minimum_required(VERSION 3.22.1)
project(signaldeck_native C)
file(GLOB CODEC2_SRC ${CMAKE_CURRENT_SOURCE_DIR}/codec2/*.c)
add_library(codec2jni SHARED ${CODEC2_SRC} codec2_jni.c)
target_include_directories(codec2jni PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/codec2)
target_compile_options(codec2jni PRIVATE -O2 -Wno-unused-variable -Wno-unused-function -Wno-unused-but-set-variable -Wno-sometimes-uninitialized)
target_link_libraries(codec2jni m log)

# sdalsa: the root audio helper for ROMs without the vendor audio service (custom ROMs / GSIs on the Armor 26 Ultra).
# An executable, not a library: it is shipped as an asset (build.gradle adds SDALSA_ASSET_DIR) and run through su.
add_executable(sdalsa sdalsa/sdalsa.c)
target_compile_options(sdalsa PRIVATE -O2 -Wall -Wextra)
target_link_options(sdalsa PRIVATE -s)
if(SDALSA_ASSET_DIR)
    set_target_properties(sdalsa PROPERTIES RUNTIME_OUTPUT_DIRECTORY ${SDALSA_ASSET_DIR}/sdalsa/${ANDROID_ABI})
endif()

# sdtrace: the device report's serial sniffer (ptrace, read only), shipped and run the same way as sdalsa.
add_executable(sdtrace sdtrace/sdtrace.c)
target_compile_options(sdtrace PRIVATE -O2 -Wall -Wextra)
target_link_options(sdtrace PRIVATE -s)
if(SDALSA_ASSET_DIR)
    set_target_properties(sdtrace PROPERTIES RUNTIME_OUTPUT_DIRECTORY ${SDALSA_ASSET_DIR}/sdtrace/${ANDROID_ABI})
endif()
