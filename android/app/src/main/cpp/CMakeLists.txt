cmake_minimum_required(VERSION 3.20)
project(nds_runner_android LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Paths
set(REPO_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../..")
get_filename_component(REPO_ROOT "${REPO_ROOT}" ABSOLUTE)

set(SDL2_DIR "${REPO_ROOT}/android/thirdparty/SDL2")
set(NDSRECOMP_DIR "${REPO_ROOT}/ndsrecomp")
set(POR_RECOMP_DIR "${REPO_ROOT}/por_recomp")
set(RECOMP_UI_DIR "${REPO_ROOT}/recomp-ui")

message(STATUS "NDS Android Build:")
message(STATUS "  REPO_ROOT:      ${REPO_ROOT}")
message(STATUS "  SDL2_DIR:       ${SDL2_DIR}")
message(STATUS "  NDSRECOMP_DIR:  ${NDSRECOMP_DIR}")
message(STATUS "  POR_RECOMP_DIR: ${POR_RECOMP_DIR}")

# Build SDL2 as a shared library
set(SDL_SHARED ON CACHE BOOL "Build SDL2 shared library" FORCE)
set(SDL_STATIC OFF CACHE BOOL "Build SDL2 static library" FORCE)
set(SDL_TEST OFF CACHE BOOL "Build SDL2 tests" FORCE)

add_subdirectory("${SDL2_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/SDL2_build" EXCLUDE_FROM_ALL)

# Recomp runner configuration
set(NDS_BOOTSTRAP_FIRMWARE ON CACHE BOOL "Bootstrap firmware" FORCE)
set(NDS_SDL_BACKEND "SDL2" CACHE STRING "Use SDL2 backend" FORCE)
set(NDS_ENABLE_COMPUTE_RENDERER OFF CACHE BOOL "Disable desktop OpenGL 4.3 compute on Android" FORCE)
set(NDS_TITLE_BANK_DIR "${POR_RECOMP_DIR}/generated" CACHE PATH "PoR generated banks" FORCE)
set(NDS_TITLE_ROM_SHA1 "c1fb223c706be6efd66827675eff2360a1605cdc" CACHE STRING "PoR SHA-1" FORCE)
set(NDS_RECOMP_UI_ROOT "${RECOMP_UI_DIR}" CACHE PATH "Recomp UI root" FORCE)

# Include the main runner project
add_subdirectory("${NDSRECOMP_DIR}/runner" "${CMAKE_CURRENT_BINARY_DIR}/runner_build")
