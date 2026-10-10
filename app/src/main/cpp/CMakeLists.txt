cmake_minimum_required(VERSION 3.22.1)
project(byedpi C)

set(CMAKE_C_STANDARD 99)
set(CMAKE_C_EXTENSIONS OFF)

add_library(byedpi SHARED
        byedpi_jni.c
        byedpi/main.c
        byedpi/packets.c
        byedpi/conev.c
        byedpi/proxy.c
        byedpi/desync.c
        byedpi/mpool.c
        byedpi/extend.c)

target_include_directories(byedpi PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/byedpi)

target_compile_definitions(byedpi PRIVATE _DEFAULT_SOURCE)
target_compile_options(byedpi PRIVATE
        -O2 -Wall -Wno-unused -Wno-unused-parameter)
