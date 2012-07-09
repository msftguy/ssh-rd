import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

import com.dd.plist.*;

import org.apache.commons.io.*;

public class Background implements Runnable {
	static Background s_background;
	static LinkedBlockingQueue<Device> s_queue;
	String ipswUrl = null;
	NSDictionary dict = null;
	Device device;
	
	Thread thread;
	
	static {
		s_queue = new LinkedBlockingQueue<Device>();

		s_background = new Background();
	};

	public static LinkedBlockingQueue<Device> getQueue() {
		return s_queue;
	}
	
	Background()
	{
		thread = new Thread(this, "Background");
	}
	
	public static void start() {
		if (s_background.thread.getState() == Thread.State.NEW) {
			s_background.thread.start();
		}
	}
	
	public static String getResourceFile(String path)
	{
		String resName = String.format("res/%s", path);
		FileOutputStream writer = null;
		InputStream is = null;
		String result = null;
		try {
			is = gui.class.getResourceAsStream(resName);
			if (is == null) {
				gui.error("Error: Cannot load resource %s", resName);
				return null;
			}
			File diskFile = new File(new File(workingDir()), path);
			diskFile.getParentFile().mkdirs(); 
			String diskPath = diskFile.toString();
			writer = new FileOutputStream(diskFile);
			IOUtils.copy(is, writer);
			gui.trace("Extracted resource to %s", diskPath);
			result = diskPath;
		} catch (IOException e) {
			gui.error("Failed to extract resource %s", resName);
			gui.exc(e);
		} finally {
			IOUtils.closeQuietly(is);
			IOUtils.closeQuietly(writer);
		}
		return result;
	}
	
	static String _workingDir = null;
	
	static String workingDir()
	{
		if (_workingDir == null) {

			File tempDir = new File(System.getProperty("java.io.tmpdir"));
			File appDir = new File(tempDir, "ssh_rd");
			appDir.mkdir();
			_workingDir = appDir.getPath();
		}
		return _workingDir.toString();
	}
	
	static String _ipswDir = null;
	
	static String ipswDir()
	{
		if (_ipswDir == null) {
			String ipswDirName = String.format("ipsw_%s_%s", 
					stringFromNsDict(s_background.dict, WebScraper.device), 
					stringFromNsDict(s_background.dict, WebScraper.build)); 
			_ipswDir = new File(new File(workingDir()), ipswDirName).getPath();
	 	}
		return _ipswDir.toString();
	}
	
	static String stringFromNsDict(NSDictionary nsd, String key) 
	{
		Object o = nsd.objectForKey(key);
		if (o == null || ! (o instanceof NSString)) {
			return null;
		}
		return ((NSString)o).toString();
	}
		
	Hashtable<String, String> filePropsByName(String name)
	{
		Hashtable<String, String> props = new Hashtable<String, String>();
		String normalizedName = name.toLowerCase();
		boolean ios5 = (null != dict.objectForKey("ios5"));
		boolean ios3 = (null != dict.objectForKey("ios3"));
		boolean ios43 = (null != dict.objectForKey("ios43"));
		String norPatch = "nor5.patch.json";
		String kernelPatch = "kernel5.patch.json";
		String wtfPatch = "wtf.patch.json";
	
		if (!ios5) {
			norPatch = (device.isWtf() && ios3) ? wtfPatch : device.isArmV6() ? "nor_armv6.patch.json" : "nor.patch.json";
			kernelPatch = device.isArmV6() ? 
						( ios3 ? "kernel3.patch.json": "kernel_armv6.patch.json" ) : 
							ios43 ? "kernel43.patch.json" : "kernel.patch.json";
		}
			
		if (normalizedName.contains("kernelcache")) {
			props.put("iv", WebScraper.kernelIV);
			props.put("key", WebScraper.kernelKey);
			props.put("patch", kernelPatch);
		} else if (normalizedName.contains("ibss")) {
			props.put("iv", WebScraper.ibssIV);
			props.put("key", WebScraper.ibssKey);
			props.put("patch", norPatch);
		} else if (normalizedName.contains("ibec")) {
			props.put("iv", WebScraper.ibecIV);
			props.put("key", WebScraper.ibecKey);	
			props.put("patch", norPatch);
		} else if (normalizedName.endsWith(".dmg")) {
			props.put("iv", WebScraper.ramdiskIV);
			props.put("key", WebScraper.ramdiskKey);	
			props.put("ramdisk", "yes");			
		} else if (normalizedName.contains("wtf")) {
			props.put("patch", wtfPatch);
		} else { // manifest, device tree, Restore.plist
			props.put("passthrough", "yes");
		}
		return props;
	}
	
	static boolean _payloadCreatedOk = false;
	static boolean _payloadCreationTest = false;

	static boolean _ramdiskSent = false;
	public static boolean ramdiskSent() 
	{
		return _ramdiskSent;
	}
	
	private static HashSet<String> s_seen = new HashSet<String>();

	static boolean getFileFromZip(String zipUrl, String zipPath, String downloadPath)
	{
		boolean spamOnce = false;
		if (!s_seen.contains(zipUrl)) {
			s_seen.add(zipUrl);
			spamOnce = true;
		}
		boolean isUrl = zipUrl.toLowerCase().startsWith("http:");
		// Try local first: 
		String zipName = zipUrl.substring(zipUrl.lastIndexOf('/') + 1); // not found => -1 + 1 = 0 => whole string
		File zipFile = new File(new File(workingDir()), zipName);
		if (isUrl && !zipFile.exists()) {
			if (spamOnce) {
				gui.log("Local file %s not found; downloading from %s", 
						zipFile.getAbsolutePath(), 
						zipUrl);
			}
			return 0 == Jsyringe.download_file_from_zip(zipUrl, zipPath, downloadPath);
		}
		while (!zipFile.exists()) {
			gui.log(gui.MessageStyle.Important, "Please put %s in the %s directory (URL not public)", zipName, workingDir());
			try {
				Thread.sleep(5* 1000);
			} catch (InterruptedException e) {
			}				
		}
		if (spamOnce) {
			gui.log("Using local file %s", zipName);
		}
		try {
			ZipFile zf = new ZipFile(zipFile);
			ZipEntry ze = zf.getEntry(zipPath);
			InputStream is = zf.getInputStream(ze);
			IOUtils.copy(is, new FileOutputStream(downloadPath));
			return true;
		} catch (IOException e) {
			gui.error("IOException unpacking %s, check IPSW", zipPath);
			gui.exc(e);
			return false;
		}
	}
	
	String downloadAndProcessFile(String zipPath) 
	{
		gui.trace("Downloading %s", zipPath);
		String finalPath = new File(new File(ipswDir()), zipPath).getPath();
		// Ensure directory exists
		File finalFile = new File(finalPath);
		if (finalFile.exists()) {
			gui.trace("Skipping processing of %s, file already exists!", finalPath);
			return finalPath;
		}
		finalFile.getParentFile().mkdirs(); 
		Hashtable<String, String>fileProps = filePropsByName(zipPath);
		boolean needsDecrypting = !fileProps.containsKey("passthrough");
		
		String downloadPath = finalPath;
		if (needsDecrypting)
			downloadPath = finalPath + ".orig";
		if (new File(downloadPath).exists()) {
			gui.trace("Skipping download of %s, file already exists!", finalPath);
		} else {
			if (!getFileFromZip(ipswUrl, zipPath, downloadPath)) {
				gui.error("Download failed! %1$s [%2$s] -> %3$s", ipswUrl, zipPath, downloadPath);
				return null;
			}
			gui.trace("Downloaded to %s", downloadPath);
		}
		
		if (needsDecrypting) {
			String decryptedPath = finalPath + ".dec";
			if (!Jsyringe.process_img3_file(downloadPath, decryptedPath, null, 
					stringFromNsDict(dict, fileProps.get("iv")), 
					stringFromNsDict(dict, fileProps.get("key")))) {
				gui.error("Decryption failed");
				return null;
			}
			gui.trace("Decrypted to %s", decryptedPath);
			String patch = fileProps.get("patch");
			if (patch != null) {
				String patchedPath = decryptedPath + ".p";
				String patchJson = Background.getResourceFile(patch);
				if (patchJson == null) {
					gui.error("getResourceFile(%s) failed, log a bug!", patch);
					return null;
				}
				if (!Jsyringe.fuzzy_patch(decryptedPath, patchedPath, patchJson, 80)) {
					gui.error("Patching failed");
					return null;
				}
				decryptedPath = patchedPath;
				gui.trace("Patched to %s", patchedPath);
			}
			if (fileProps.containsKey("ramdisk")) {
				String sshTarFile = Background.getResourceFile("ssh.tar");
				if (sshTarFile == null) {
					gui.error("getResourceFile(ssh.tar) failed, log a bug!");
					return null;
				}
				long extend;
				long tarLength = new File(sshTarFile).length();
				if (tarLength == 0) {
					gui.error("Can't get tar file size!");
					return null;
				}
				extend = (long)(1.05 * (double)(tarLength));
				if (!Jsyringe.add_ssh_to_ramdisk(decryptedPath, sshTarFile, extend)) {
					gui.error("Adding ssh to ramdisk failed!");
					return null;
				}
				gui.trace("Added ssh.tar to the ramdisk");
			}
			if (!Jsyringe.process_img3_file(decryptedPath, finalPath, downloadPath, 
					stringFromNsDict(dict, fileProps.get("iv")),
					stringFromNsDict(dict, fileProps.get("key")))) {
				gui.error("Encryption failed");
				return null;
			}
		}
		return finalPath;
	}
	
	boolean fetchKeysFromWiki() 
	{
		NSDictionary plDict = new NSDictionary(); 
		int cSkipped = 0;
		for (DeviceProps dp : Device.supportedDevices) {
			if (dp.isDfuStub)
				continue;
			ArrayList<String> urls = WebScraper.getFirmwareUrls(dp.apName);
			boolean ok = false;
			Hashtable<String,String>dict = null;
			for (int fwPageIndex = urls.size() - 1; fwPageIndex >= 0 ; --fwPageIndex) {
				String url = urls.get(fwPageIndex);
				gui.trace("wiki URL: %s", url);
				dict = WebScraper.loadAndParseFirmwarePage(url);
				if (dict == null)
					continue;
				for (Iterator<String> it = WebScraper.displayFields.iterator(); it.hasNext(); ) { 
	    			String key = it.next();
	    			String value = dict.get(key);
	    			if (value != null) {
	    				gui.trace("%s\t: %s", key, value);
	    			}
				}
				gui.trace("Enough keys: %s", WebScraper.hasEnoughKeys(dict) ? "YES" : "NO");
				
				if (WebScraper.hasEnoughKeys(dict)) {
					ok = true;
					break;
				}
			}
			if (ok && dict != null) {
				NSDictionary nsDict = new NSDictionary(); 
				Iterator<String> it = dict.keySet().iterator();
				while(it.hasNext()) {
					String key = it.next();
					String val = dict.get(key);
					nsDict.put(key, val);
				}
				plDict.put(dp.apName, nsDict);
				gui.trace("Added %s!", dp.apName);				
			} else {
				++cSkipped;
				gui.trace("Skipped %s!", dp.apName);
			}
		}
		if (cSkipped != 0)
			return false;
		try {
			PropertyListParser.saveAsXML(plDict, new File("/tmp/all_keys.plist"));
			gui.success("Saved everything to file!");
			return true;
		} catch (IOException e1) {
			gui.error("Fetching keys from TheIphoneWiki failed!");
			gui.exc(e1);
		}
		return false;
	}
	
	void runTests()
	{
		ArrayList<DeviceProps> dps = Device.__TEST__getSupportedDevices();
		int cErrors = 0;
		for (DeviceProps dp : dps)
		{
			int pType = dp.productCode;
			if ((dp.productCode & 0xffff) != dp.productChip) {
				pType = 0x12220000 + dp.productCode;
			}
			_payloadCreationTest = true;
			_payloadCreatedOk = false;
 			Device dev = new Device(0x1222, pType);
			onDfuDeviceArrival(dev);
			if (!_payloadCreatedOk) {
				gui.error("Error testing %s", dev.getName());
				++cErrors;
			} else {
				gui.success("Device %s passed!", dev.getName());
			}
		}
		if (cErrors != 0) {
			gui.error("There were %d errors!", cErrors);
		} else 
			gui.success("All devices passed!");
	}
	
	public void run()
	{
		try {
			if (gui.getTestOption()) {
				runTests();
			} else if (gui.getFetchOption()) {
				fetchKeysFromWiki();
			} else {
				while (true) {
					Device d = s_queue.poll(1, TimeUnit.SECONDS);
					if (d != null)
						onDfuDeviceArrival(d);				
				}
			}
		} catch (Exception e) {
			gui.error("!! FAIL: Unhandled exception in background thread: %s, %s", e.toString(), e.getMessage());
			gui.exc(e);
		}
	}
	
	void onDfuDeviceArrival(Device dev) 
	{
		gui.trace("DFU device '%s' connected", dev.getName());
		if (dev.isUnsupported()) {
			gui.error("Ignoring unsupported device %s", dev.getName());
			return;
		}
		if (this.device != null && this.device.getName().equals(dev.getName())) {
			gui.trace("Ignoring same device %s", dev.getName());
			return;
		}
		this.device = dev;
		prepareRamdiskForDevice();
	}
	
	void prepareRamdiskForDevice()
	{	
		gui.log(gui.MessageStyle.Important, "Building ramdisk for device '%s'", device.getName());
		_ipswDir = null;
		String keyFileName = Background.getResourceFile("all_keys.plist");
		NSDictionary plDict;
		try {
			plDict = (NSDictionary)PropertyListParser.parse(new File(keyFileName));
		} catch (Exception e1) {
			gui.error("Cannot load all_keys.plist from resources; bailing !");
			gui.exc(e1);
			return;
		}
		dict = (NSDictionary)plDict.objectForKey(device.getAp());

		gui.trace("Working dir set to %s", workingDir());
		
		ipswUrl = stringFromNsDict(dict, WebScraper.downloadUrl);
		
		gui.trace("IPSW at %s", ipswUrl);
		
		if (device.isWtfStub()) {
			dict.put(WebScraper.device, "dfu8900");
		}
		
		String restorePlistFile = downloadAndProcessFile("Restore.plist");
		if (restorePlistFile == null) {
			gui.error("Restore.plist download failed!");
			return;
		}
		gui.trace("Restore.plist downloaded to %s", restorePlistFile);
		
		gui.trace("Parsing Restore.plist..");
		
		File restorePlist = new File(restorePlistFile);
		
		NSDictionary restoreDict = null;
		try {
			restoreDict = (NSDictionary)PropertyListParser.parse(restorePlist);
		} catch (Exception e) {
			gui.error("Can't parse Restore.plist, bailing!");
			e.printStackTrace();
			return;
		}
		
		String iosVersion = stringFromNsDict(restoreDict, "ProductVersion");
		String[] verComponents = iosVersion.split("\\.");
		String iosVerMajor = verComponents[0];
		dict.put("ios", iosVerMajor);
		dict.put("ios" + iosVerMajor, "yes"); //ios5, ios4, ios3
		String iosVerMinor = "0";
		if (verComponents.length > 1) {
			iosVerMinor = verComponents[1];
		}
		dict.put("ios" + iosVerMajor + iosVerMinor, "yes");
		
		NSDictionary kcByTargetDict = (NSDictionary)restoreDict.objectForKey("KernelCachesByTarget");
		NSDictionary kcDict = null;
		if (kcByTargetDict != null) {
			String modelNoAp = device.getAp().replaceAll("ap$", "");
			kcDict = (NSDictionary)kcByTargetDict.objectForKey(modelNoAp);
		} else {
			kcDict = (NSDictionary)restoreDict.objectForKey("RestoreKernelCaches");
		}
		String kernelName = stringFromNsDict(kcDict, "Release");		
		gui.trace("Kernel file: %s", kernelName);
		
		NSDictionary ramdisksDict = (NSDictionary)restoreDict.objectForKey("RestoreRamDisks");
		String ramdiskName = stringFromNsDict(ramdisksDict, "User");		
		gui.trace("Restore ramdisk file: %s", ramdiskName);
		
		String dfuFolder = "Firmware/dfu/";
		String ibssName = String.format("iBSS.%s.RELEASE.dfu", device.getAp());
		String ibssPath = dfuFolder.concat(ibssName);
		
		if (!device.isWtfStub()) {
			String ibssFile = downloadAndProcessFile(ibssPath);
		
			if (ibssFile == null) {
				gui.error("iBSS download failed!");
				return;
			}
			gui.trace("iBSS prepared at %s", ibssFile);
		}
		
		String ibecFile = null;
		if (null != dict.objectForKey("ios5")) {
			String ibecName = String.format("iBEC.%s.RELEASE.dfu", device.getAp());
			String ibecPath =  dfuFolder.concat(ibecName);
			
			ibecFile = downloadAndProcessFile(ibecPath);
			
			if (ibecFile == null) {
				gui.error("iBEC download failed!");
				return;
			}
			gui.trace("iBEC prepared at %s", ibecFile);
		}

		String wtf8900File = null;
		String wtfModelFile = null;
		if (device.isWtf() || device.isWtfStub()) {
			String wtf8900Name = "WTF.s5l8900xall.RELEASE.dfu";
			String wtf8900Path =  dfuFolder.concat(wtf8900Name);
			String wtfModelName = String.format("WTF.%s.RELEASE.dfu", device.getAp());;
			String wtfModelPath =  dfuFolder.concat(wtfModelName);
			
			wtf8900File = downloadAndProcessFile(wtf8900Path);
			
			if (wtf8900File == null) {
				gui.error("WTF.s5l8900xall download failed!");
				return;
			}
			gui.trace("WTF.s5l8900xall prepared at %s", wtf8900File);
		
			if (!device.isWtfStub()) {
				wtfModelFile = downloadAndProcessFile(wtfModelPath);
					
				if (wtfModelFile == null) {
					gui.error("%s download failed!", wtfModelName);
					return;
				}
				
				gui.trace("%s prepared at %2s", wtfModelName, wtfModelFile);
			}
		}
		
		if (!device.isWtfStub()) {
			String deviceTreeName =  String.format("DeviceTree.%s.img3", device.getAp());
		
			String deviceTreePath = String.format("Firmware/all_flash/all_flash.%s.production/%s", device.getAp(), deviceTreeName);
		
			String deviceTreeFile = downloadAndProcessFile(deviceTreePath);
		
			if (deviceTreeFile == null) {
				gui.error("Device tree download failed!");
				return;
			}
			gui.trace("Device tree prepared at %s", deviceTreeFile);
		

			String manifestPath = String.format("Firmware/all_flash/all_flash.%s.production/manifest", device.getAp());
		
			String manifestFile = downloadAndProcessFile(manifestPath);
		
			if (manifestFile == null) {
				gui.error("Manifest download failed!");
				return;
			}
		
			String kernelFile = downloadAndProcessFile(kernelName);
			
			if (kernelFile == null) {
				gui.trace("Kernel download failed!");
				return;
			}
	
			gui.trace("Kernel prepared at %s", kernelFile);
			
			String ramdiskFile = downloadAndProcessFile(ramdiskName);
			
			if (ramdiskFile == null) {
				gui.error("Ramdisk download failed!");
				return;
			}
			gui.trace("Ramdisk prepared at %s", ramdiskFile);
			
			if (_payloadCreationTest) {
				_payloadCreatedOk = true;
				return;
			}
	
			if (!device.isWtf()) {
				gui.log("Using syringe to exploit the bootrom..");
				if (0 != Jsyringe.exploit()) {
					gui.error("Exploiting the device failed!");
					return;
				}
				gui.success("Exploit sent!");
			}
		} // endif (!device.isWtfStub())
		if (_payloadCreationTest) {
			_payloadCreatedOk = true;
			return;
		}
		
		if (!device.isWtfStub()) {
			gui.log("Preparing to load the ramdisk..");
			_ramdiskSent = true;
		} else
			gui.log("Trying to pwn 8900 DFU mode..");
			
		if (!Jsyringe.restore_bundle(ipswDir())) {
			if (!device.isWtfStub()) 
				gui.error("Failed to use iTunes API to load the ramdisk!");
			else
				gui.error("Failed to use iTunes API to load the 8900 exploit!");
			return;
		}
		if (!device.isWtfStub()) 
			gui.log("Ramdisk load started!");
		 else
			gui.log("8900 exploit load started!");
	}
}
