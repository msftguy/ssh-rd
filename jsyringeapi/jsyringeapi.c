#include <jsyringe/jsyringe.h>
#include "jni_helper.h"
//#include <jni.h>
#include <libpartial.h>
#include <libpois0n.h>


static const char* g_model = NULL;

/*
 * Class:     Jsyringe
 * Method:    download_file_from_zip
 * Signature: (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_Jsyringe_download_1file_1from_1zip
  (JNIEnv * env, jclass jClassObj, jstring Jurl, jstring Jpath, jstring Joutput)
{
	jint result = -1;
	const char *url, *path, *output;
	J2C(url);
	J2C(path);
	J2C(output);

	result = download_file_from_zip(url, path, output, NULL /*no callback just yet*/);

	JFREE(url);
	JFREE(path);
	JFREE(output);
	return result;
}

/*
 * Class:     Jsyringe
 * Method:    wait_for_connect
 * Signature: (I)Ljava/lang/Boolean;
 */
JNIEXPORT jboolean JNICALL Java_Jsyringe_wait_1for_1connect
  (JNIEnv *env, jclass jClass)
{
	jboolean jresult = JNI_FALSE;
	if (g_syringe_client != NULL) {
		irecv_close(&g_syringe_client);
	}
	if (0 == irecv_open(&g_syringe_client) && g_syringe_client->mode == kDfuMode) {
		if (irecv_get_device(g_syringe_client, &g_syringe_device) == IRECV_E_SUCCESS) {
			g_model = g_syringe_device->model;
			jresult = JNI_TRUE;
		}
	}
cleanup:
	if (g_syringe_client != NULL)
		irecv_close(&g_syringe_client);
	return jresult;
}


#define info(...) {fprintf(stderr, __VA_ARGS__); fflush(stderr);}
#define error(...) {fprintf(stderr, __VA_ARGS__); fflush(stderr);}
#define debug(...) {fprintf(stderr, __VA_ARGS__); fflush(stderr);}

#ifdef WIN32

__declspec(dllexport) int tethered_boot(const char *ibssFile, const char *ibecFile, const char *kernelcacheFile, const char *ramdiskFile, const char *devicetreeFile);


int tethered_boot(const char *ibssFile, const char *ibecFile, const char *kernelcacheFile, const char *ramdiskFile, const char *devicetreeFile)
{
	int result = 0;
	irecv_error_t ir_error = IRECV_E_SUCCESS;
	irecv_client_t client = g_syringe_client;

	libpois0n_debug = 1;

	pois0n_init();

	info("Waiting for device to enter DFU mode\n");
	while(pois0n_is_ready()) {
		sleep(1);
	}

	info("Found device in DFU mode\n");
	result = pois0n_is_compatible();
	if (result < 0) {
		error("Your device in incompatible with this exploit!\n");
		goto cleanup;
	}

	result = pois0n_injectonly();
	if (result < 0) {
		error("Exploit injection failed!\n");
		goto cleanup;
	}
	client = g_syringe_client;

	if (ibssFile != NULL) {
		debug("Uploading %s to device, mode: 0x%x\n", ibssFile, client->mode);
		ir_error = irecv_send_file(client, ibssFile, 1);
		if(ir_error != IRECV_E_SUCCESS) {
			error("Unable to upload iBSS\n");
			debug("%s\n", irecv_strerror(ir_error));
			result = -1;
			goto cleanup;
		}
		
		sleep(10);

	} else {
		error("ibss can't be null\n");
		result = -1;
		goto cleanup;
	}

	if (ibecFile != NULL) {
		client = g_syringe_client = irecv_reconnect(client, 10);

		debug("Uploading iBEC %s to device, mode: 0x%x\n", ibecFile, client->mode);
		ir_error = irecv_send_file(client, ibecFile, 1);
		if(ir_error != IRECV_E_SUCCESS) {
			error("Unable to upload iBEC\n");
			debug("%s\n", irecv_strerror(ir_error));
			result = -1;
			goto cleanup;
		}

		sleep(5);
	}

	client = g_syringe_client = irecv_reconnect(client, 10);

	if (ramdiskFile != NULL) {
		debug("Uploading ramdisk %s to device\n", ramdiskFile);
		ir_error = irecv_send_file(client, ramdiskFile, 1);
		if(ir_error != IRECV_E_SUCCESS) {
			error("Unable to upload ramdisk\n");
			debug("%s\n", irecv_strerror(ir_error));
			result = -1;
			goto cleanup;
		}

		sleep(5);

		ir_error = irecv_send_command(client, "ramdisk");
		if(ir_error != IRECV_E_SUCCESS) {
			error("Unable send the ramdisk command\n");
			result = -1;
			goto cleanup;
		}	
	}

	if (devicetreeFile != NULL) {
	        debug("Uploading device tree %s to device\n", devicetreeFile);
		ir_error = irecv_send_file(client, devicetreeFile, 1);
		if(ir_error != IRECV_E_SUCCESS) {
			error("Unable to upload device tree\n");
			debug("%s\n", irecv_strerror(ir_error));
			result = -1;
			goto cleanup;
		}

		ir_error = irecv_send_command(client, "devicetree");
		if(ir_error != IRECV_E_SUCCESS) {
			error("Unable to send the devicetree command\n");
			result = -1;
			goto cleanup;
		}
	}
	
	if (kernelcacheFile != NULL) {
		debug("Uploading kernel %s to device, mode: 0x%x\n", kernelcacheFile, client->mode);
		ir_error = irecv_send_file(client, kernelcacheFile, 1);
		if(ir_error != IRECV_E_SUCCESS) {
			error("Unable to upload kernelcache\n");
			debug("%s\n", irecv_strerror(ir_error));
			result = -1;
			goto cleanup;
		}

		ir_error = irecv_send_command(client, "bootx");
		if(ir_error != IRECV_E_SUCCESS) {
			error("Unable send the bootx command\n");
			result = -1;
			goto cleanup;
		}
	} else {
		error("kernelcache can't be null\n");
		result = -1;
		goto cleanup;
	}

	result = 0;

cleanup:
	fflush(stderr);
	if (g_syringe_client) {
		irecv_close(&g_syringe_client);
		g_syringe_client = NULL;
	}
	
	//pois0n_exit();
	return result;
}

#endif

//
///*
// * Class:     Jsyringe
// * Method:    tethered_boot
// * Signature: (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I
// */
//JNIEXPORT jint JNICALL Java_Jsyringe_tethered_1boot
//  (JNIEnv * env, jclass jClass, jstring JibssFile, jstring JibecFile, jstring JkernelcacheFile, jstring JramdiskFile, jstring JdevicetreeFile)
//{
//	const char *ibssFile, *ibecFile, *kernelcacheFile, *ramdiskFile, *devicetreeFile;
//	int result = 0;
//
//	J2C(ibssFile);
//	J2C(ibecFile);
//	J2C(kernelcacheFile);
//	J2C(ramdiskFile);
//	J2C(devicetreeFile);
//
//	result = tethered_boot(ibssFile, ibecFile, kernelcacheFile, ramdiskFile, devicetreeFile);
//
//	JFREE(ibssFile);
//	JFREE(ibecFile);
//	JFREE(kernelcacheFile);
//	JFREE(ramdiskFile);
//	JFREE(devicetreeFile);
//
//	return result;
//}

/*
 * Class:     Jsyringe
 * Method:    get_device_model
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_Jsyringe_get_1device_1model
  (JNIEnv *env, jclass jClass)
{
	return (*env)->NewStringUTF(env, g_model == NULL ? "" : g_model);
}

#ifdef WIN32

BOOL WINAPI DllMain(
  __in  HINSTANCE hinstDLL,
  __in  DWORD fdwReason,
  __in  LPVOID lpvReserved
)
{
    if (fdwReason == DLL_PROCESS_ATTACH) {
		irecv_init();
	}
	return TRUE;
}

#else 

extern void initializer_proc() __attribute__((constructor));
void initializer_proc()
{
    irecv_init();
}

#endif //WIN32


/*
 * Class:     Jsyringe
 * Method:    exploit
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_Jsyringe_exploit
  (JNIEnv * env, jclass jClass)
{
	int result = 0;
	irecv_error_t ir_error = IRECV_E_SUCCESS;
	irecv_client_t client = g_syringe_client;

	libpois0n_debug = 1;

	pois0n_init();

	info("Waiting for device to enter DFU mode\n");
	while(pois0n_is_ready()) {
		sleep(1);
	}

	info("Found device in DFU mode\n");
	result = pois0n_is_compatible();
	if (result < 0) {
		error("Your device in incompatible with this exploit!\n");
		goto cleanup;
	}

	result = pois0n_injectonly();
	if (result < 0) {
		error("Exploit injection failed!\n");
		goto cleanup;
	}
	result = 0;

cleanup:
	fflush(stderr);
	if (g_syringe_client) {
		irecv_close(&g_syringe_client);
		g_syringe_client = NULL;
	}
	
	//pois0n_exit();
	return result;
}
// ping 
// irecv_get_string_descriptor_ascii