cmake_minimum_required(VERSION 3.16)

project(bitchord_linux_audio LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

foreach(suffix "" _DEBUG _RELEASE _RELWITHDEBINFO _MINSIZEREL)
    set(CMAKE_LIBRARY_OUTPUT_DIRECTORY${suffix} ${CMAKE_BINARY_DIR})
    set(CMAKE_RUNTIME_OUTPUT_DIRECTORY${suffix} ${CMAKE_BINARY_DIR})
endforeach()

add_library(bitchord_linux_audio SHARED linux_audio_jni.cpp)

if(DEFINED ENV{JAVA_HOME})
    target_include_directories(bitchord_linux_audio PRIVATE
        $ENV{JAVA_HOME}/include
        $ENV{JAVA_HOME}/include/linux
    )
else()
    find_package(JNI REQUIRED)
    target_include_directories(bitchord_linux_audio PRIVATE ${JNI_INCLUDE_DIRS})
endif()

target_compile_options(bitchord_linux_audio PRIVATE -O2)
target_link_libraries(bitchord_linux_audio PRIVATE ${CMAKE_DL_LIBS})
