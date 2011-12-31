#include <jsyringe/jsyringe.h>
#include "jni_helper.h"
#include <xpwndll.h>
#include <hfsdll.h>

/*
 * Class:     Jsyringe
 * Method:    process_img3_file
 * Signature: (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_Jsyringe_process_1img3_1file
  (JNIEnv * env, jclass jClass, jstring Jsrc, jstring Jdst, jstring Jtmpl, jstring Jiv, jstring Jkey)
{
	jboolean result = JNI_FALSE;
	const char *src, *dst, *tmpl, *iv, *key;

	J2C(src);
	J2C(dst);
	J2C(tmpl);
	J2C(iv);
	J2C(key);

	if (0 == xpwntool_enc_dec(src, dst, tmpl, iv, key)) {
		result = JNI_TRUE;
	}
cleanup:

	JFREE(src);
	JFREE(dst);
	JFREE(tmpl);
	JFREE(iv);
	JFREE(key);
	return result;
}

/*
 * Class:     Jsyringe
 * Method:    add_ssh_to_ramdisk
 * Signature: (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_Jsyringe_add_1ssh_1to_1ramdisk
  (JNIEnv * env, jclass jClass, jstring Jdmg, jstring JtarFile, jlong extendBy)
{
	jboolean result = JNI_FALSE;
	uint64_t size;
	HfsContext* hctx = NULL;
	const char* dmg, *tarFile;
	
	J2C(dmg);
	J2C(tarFile);

	hctx = hfslib_open(dmg);
	if (!hctx) 
		goto cleanup;
	size = hfslib_getsize(hctx);
	size += extendBy;
	if(!hfslib_extend(hctx, size))
		goto cleanup;
	if (hfslib_untar(hctx, tarFile)) {
		result = JNI_TRUE;
	}
cleanup:
	if (hctx)
		hfslib_close(hctx);

	JFREE(dmg);
	JFREE(tarFile);
	
	return result;
}
