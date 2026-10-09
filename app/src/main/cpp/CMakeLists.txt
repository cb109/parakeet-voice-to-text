cmake_minimum_required(VERSION 3.22.1)
project(whistle_jni LANGUAGES C CXX)

set(NEEDLE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/needle/${ANDROID_ABI})

add_library(needle STATIC IMPORTED)
set_target_properties(needle PROPERTIES IMPORTED_LOCATION ${NEEDLE_DIR}/libneedle.a)

add_library(whistle_jni SHARED whistle_jni.cpp)
target_include_directories(whistle_jni PRIVATE ${NEEDLE_DIR})
target_link_libraries(whistle_jni needle log m)
target_link_options(whistle_jni PRIVATE "-Wl,-z,max-page-size=16384")
