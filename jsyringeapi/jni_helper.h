#pragma once

#ifdef WIN32 
#define INLINE __inline
#else
#define INLINE inline static
#endif

INLINE const char* j2utf(JNIEnv * env, jstring js)
{
	return js == NULL ? NULL : (*env)->GetStringUTFChars(env, js, 0);
}

INLINE void j2free(JNIEnv * env, jstring js, const char* s) 
{
	if (js != NULL && s != NULL) {
		(*env)->ReleaseStringUTFChars(env, js, s);
	}
}

#define J2C(x) {x = j2utf(env, J##x);}
#define JFREE(x) j2free(env, J##x, x)

#define GetStringUTFChars <<<<<<
#define ReleaseStringUTFChars <<<<<<
