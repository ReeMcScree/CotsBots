@echo off
"C:\\Users\\xiang\\AppData\\Local\\Android\\Sdk\\cmake\\3.22.1\\bin\\cmake.exe" ^
  "-HC:\\Users\\xiang\\Documents\\GitHub\\CotsBots\\OpenCV Tracking\\OpenCV\\libcxx_helper" ^
  "-DCMAKE_SYSTEM_NAME=Android" ^
  "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON" ^
  "-DCMAKE_SYSTEM_VERSION=29" ^
  "-DANDROID_PLATFORM=android-29" ^
  "-DANDROID_ABI=x86" ^
  "-DCMAKE_ANDROID_ARCH_ABI=x86" ^
  "-DANDROID_NDK=C:\\Users\\xiang\\AppData\\Local\\Android\\Sdk\\ndk\\27.0.12077973" ^
  "-DCMAKE_ANDROID_NDK=C:\\Users\\xiang\\AppData\\Local\\Android\\Sdk\\ndk\\27.0.12077973" ^
  "-DCMAKE_TOOLCHAIN_FILE=C:\\Users\\xiang\\AppData\\Local\\Android\\Sdk\\ndk\\27.0.12077973\\build\\cmake\\android.toolchain.cmake" ^
  "-DCMAKE_MAKE_PROGRAM=C:\\Users\\xiang\\AppData\\Local\\Android\\Sdk\\cmake\\3.22.1\\bin\\ninja.exe" ^
  "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=C:\\Users\\xiang\\Documents\\GitHub\\CotsBots\\OpenCV Tracking\\OpenCV\\build\\intermediates\\cxx\\Debug\\3n29e654\\obj\\x86" ^
  "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=C:\\Users\\xiang\\Documents\\GitHub\\CotsBots\\OpenCV Tracking\\OpenCV\\build\\intermediates\\cxx\\Debug\\3n29e654\\obj\\x86" ^
  "-DCMAKE_BUILD_TYPE=Debug" ^
  "-BC:\\Users\\xiang\\Documents\\GitHub\\CotsBots\\OpenCV Tracking\\OpenCV\\.cxx\\Debug\\3n29e654\\x86" ^
  -GNinja ^
  "-DANDROID_STL=c++_shared"
