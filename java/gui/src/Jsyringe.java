public class Jsyringe {
	private static final String nativeDir = "native";
	private static final String native_lib = "jsyringeapi";
	private static final String native_lib_helper = "mux_redux";

	// Native method declaration
	// PartialZip
	public static native int download_file_from_zip(String url,
			String pathInZip, String outputFile);

	// Syringe
//	public static native boolean wait_for_connect();
//
//	public static native String get_device_model();

	// public static native int tethered_boot(String ibssFile, String ibecFile,
	// String kernelcacheFile, String ramdiskFile, String devicetreeFile);
	public static native int exploit();

	// Xpwn
	public static native boolean process_img3_file(String infile,
			String outfile, String template, String IV, String Key);

	// DMG library
	public static native boolean add_ssh_to_ramdisk(String dmg,
			String sshTarfile, long extendBy);

	// Fuzzy patching
	public static native boolean fuzzy_patch(String infile, String outfile,
			String patchFile, int fuzzLevel);

	// iTMD API
	public static native boolean restore_bundle(String bundlePath);
	public static native void runMobileDeviceThread(MobileDevice mobileDevice);
	public static native boolean startMuxThread(int iport, int lport);


	// Load the library
	static boolean init()
	{
		try {
			String jsapi_tmp = copyFromJar(native_lib);
			copyFromJar(native_lib_helper);
			System.load(jsapi_tmp);
			return true;
		} catch (UnsatisfiedLinkError ule) {
			gui.err(ule);
		} catch (Exception e) {
			gui.exc(e);
		}
		gui.error("FATAL: Cannot load native libraries; make sure you're using 32-bit JRE if on Windows!");
		return false;
	}

	static String osLibExtension() {
		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("windows"))
			return "dll";
		if (os.contains("mac os x"))
			return "jnilib";
		return null;
	}

	static String copyFromJar(String lib) {
		String resPath = String.format("%1$s/%2$s.%3$s", nativeDir, lib, osLibExtension());
		return Background.getResourceFile(resPath);
	}
}
