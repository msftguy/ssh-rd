#include <jsyringe/jsyringe.h>
#include "jni_helper.h"
#include <fuzzy_patcher/patcher.h>
#include <mux_redux/mux_load.h>
#include <mux_redux/mux_api.h>

/*
 * Class:     Jsyringe
 * Method:    fuzzy_patch
 * Signature: (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)Z
 */
JNIEXPORT jboolean JNICALL Java_Jsyringe_fuzzy_1patch
  (JNIEnv * env, jclass jClass, jstring Jorig, jstring Jpatched, jstring Jdelta, jint fuzz)
{
	jboolean result = JNI_FALSE;
	const char *orig, *patched, *delta;

	J2C(orig);
	J2C(patched);
	J2C(delta);

	init_diff_byte_rating();

	g_patcher_fuzzLevel = fuzz;

	if (0 == patchFiles(orig, patched, delta)) {
		result = JNI_TRUE;
	}
	
	JFREE(orig);
	JFREE(patched);
	JFREE(delta);

	return result;
}



/*
 * Class:     Jsyringe
 * Method:    restore_bundle
 * Signature: (Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_Jsyringe_restore_1bundle
  (JNIEnv * env, jclass jClass, jstring JbundlePath)
{
	jboolean jresult = JNI_FALSE;
	char errorMessage[0x100];
	const char *bundlePath;
	int result;
	J2C(bundlePath);

	memset(errorMessage, 0, sizeof(errorMessage));
	result = itmd_load(errorMessage, sizeof(errorMessage) - 1);
	if (result == 0) {
		itmd_restoreBundle(bundlePath);
		jresult = JNI_TRUE;
	} else {
		fprintf(stderr, "%s", errorMessage);
		fflush(stderr);
	}

	JFREE(bundlePath);
	return jresult;
}