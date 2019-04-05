##include <jni.h>
##include <string>

extern "C"
JNIEXPORT jstring
JNICALL
main(JNIEnv *env, jobject /* this*/) {
  std::string hello = "Hello";
  return env->NewStringUTF(hello.c_str());
}
