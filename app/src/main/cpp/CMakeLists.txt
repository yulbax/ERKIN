cmake_minimum_required(VERSION 3.22.1)
project(byedpi C)

set(CMAKE_C_STANDARD 99)
set(CMAKE_C_EXTENSIONS OFF)

add_executable(ciadpi
        byedpi/main.c
        byedpi/packets.c
        byedpi/conev.c
        byedpi/proxy.c
        byedpi/desync.c
        byedpi/mpool.c
        byedpi/extend.c)

set_target_properties(ciadpi PROPERTIES OUTPUT_NAME "libciadpi.so")

target_include_directories(ciadpi PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/byedpi)

target_compile_definitions(ciadpi PRIVATE _DEFAULT_SOURCE)
target_compile_options(ciadpi PRIVATE
        -O2 -Wall -Wno-unused -Wno-unused-parameter)
