#include <stdio.h>
#include <unistd.h>
#include <jni.h>

JNIEXPORT jstring JNICALL Java_rice_email_proxy_password_getPassword (JNIEnv *env, jobject obj)
{
  char *ptr;
  ptr = getpass("");
  return (*env)->NewStringUTF(env, ptr);
}