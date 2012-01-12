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

static jboolean s_itmd_loaded = JNI_FALSE;

static jboolean itmd_ensure()
{
	char errorMessage[0x100];
	int result;
	if (s_itmd_loaded)
		return JNI_TRUE;
	memset(errorMessage, 0, sizeof(errorMessage));
	result = itmd_load(errorMessage, sizeof(errorMessage) - 1);
	if (result == 0) {
		s_itmd_loaded = JNI_TRUE;
	} else {
		fprintf(stderr, "%s", errorMessage);
		fflush(stderr);
	}
	return s_itmd_loaded;
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
	const char *bundlePath;
	J2C(bundlePath);
	if (!itmd_ensure())
		goto cleanup;
	itmd_restoreBundle(bundlePath);
	jresult = JNI_TRUE;
cleanup:
	JFREE(bundlePath);
	return jresult;
}


typedef struct {
    JNIEnv * env;
    jclass jClass;
    jobject jObject;
} JAVA_CALLBACK_CONTEXT, *PJAVA_CALLBACK_CONTEXT;

void javaMobileDeviceCallbackProc(void* ctx, int eventType, int productId, int productType)
{
    PJAVA_CALLBACK_CONTEXT pctx = (PJAVA_CALLBACK_CONTEXT)ctx;
        
    jclass mobileDeviceClass = (*pctx->env)->GetObjectClass(pctx->env, pctx->jObject);
    //const char* callbackSignature = "(ILjava/lang/String;Ljava/lang/String;)V";
    const char* callbackSignature = "(III)V";
    jmethodID mid = (*pctx->env)->GetMethodID(pctx->env, mobileDeviceClass, "callback", callbackSignature);
    if (mid == 0) {
        goto cleanup;
    }
    (*pctx->env)->CallVoidMethod(pctx->env, pctx->jObject, mid, eventType, productId, productType);
cleanup:
    (*pctx->env)->DeleteLocalRef(pctx->env, mobileDeviceClass);
    return;
}

/*
 * Class:     Jsyringe
 * Method:    runMobileDeviceThread
 * Signature: (LMobileDevice;)V
 */
JNIEXPORT void JNICALL Java_Jsyringe_runMobileDeviceThread
(JNIEnv * env, jclass jClass, jobject jObject)
{
    PJAVA_CALLBACK_CONTEXT pctx = (PJAVA_CALLBACK_CONTEXT)malloc(sizeof(JAVA_CALLBACK_CONTEXT));
    pctx->env = env;
    pctx->jClass = jClass;
    pctx->jObject = jObject;
    
    if (!itmd_ensure())
		goto cleanup;

	itmd_run(javaMobileDeviceCallbackProc, pctx);

cleanup:  

    return;
}

/*
 * Class:     Jsyringe
 * Method:    startMuxThread
 * Signature: (II)Z
 */
JNIEXPORT jboolean JNICALL Java_Jsyringe_startMuxThread
(JNIEnv * env, jclass jClass, jint iport, jint lport)
{
    jboolean jresult = JNI_FALSE;
	if (!itmd_ensure())
		goto cleanup;

	if (0 == itmd_start_mux_tunnel(lport, iport))
		jresult = JNI_TRUE;

cleanup:
	return jresult;
}

