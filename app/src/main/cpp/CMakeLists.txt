# SignalDeck native code: Codec 2 (LGPL-2.1, codec2/COPYING) for M17 voice, and its JNI bridge.
cmake_minimum_required(VERSION 3.22.1)
project(signaldeck_native C)
file(GLOB CODEC2_SRC ${CMAKE_CURRENT_SOURCE_DIR}/codec2/*.c)
add_library(codec2jni SHARED ${CODEC2_SRC} codec2_jni.c)
target_include_directories(codec2jni PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/codec2)
target_compile_options(codec2jni PRIVATE -O2 -Wno-unused-variable -Wno-unused-function -Wno-unused-but-set-variable -Wno-sometimes-uninitialized)
target_link_libraries(codec2jni m log)
