cmake_minimum_required(VERSION 3.22.1)

project("s2test")

add_library(
    s2test
    SHARED
    s2_jni.cpp
)

# S2
add_library(
    s2
    SHARED
    IMPORTED
)

set_target_properties(
    s2
    PROPERTIES
    IMPORTED_LOCATION
    "${CMAKE_CURRENT_SOURCE_DIR}/../../jniLibs/${ANDROID_ABI}/libs2.so"
)

# ggml-base
add_library(
    ggml-base
    SHARED
    IMPORTED
)

set_target_properties(
    ggml-base
    PROPERTIES
    IMPORTED_LOCATION
    "${CMAKE_CURRENT_SOURCE_DIR}/../../jniLibs/${ANDROID_ABI}/libggml-base.so"
)

# ggml-cpu
add_library(
    ggml-cpu
    SHARED
    IMPORTED
)

set_target_properties(
    ggml-cpu
    PROPERTIES
    IMPORTED_LOCATION
    "${CMAKE_CURRENT_SOURCE_DIR}/../../jniLibs/${ANDROID_ABI}/libggml-cpu.so"
)

# ggml
add_library(
    ggml
    SHARED
    IMPORTED
)

set_target_properties(
    ggml
    PROPERTIES
    IMPORTED_LOCATION
    "${CMAKE_CURRENT_SOURCE_DIR}/../../jniLibs/${ANDROID_ABI}/libggml.so"
)

target_include_directories(
    s2test
    PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/include"
    "${CMAKE_CURRENT_SOURCE_DIR}/third_party"
)

find_library(
    log-lib
    log
)

target_link_libraries(
    s2test
    s2
    ggml
    ggml-cpu
    ggml-base
    ${log-lib}
)