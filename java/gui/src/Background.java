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
		String resName = String.format("res/%1s", path);
		FileOutputStream writer = null;
		InputStream is = null;
		String result = null;
		try {
			is = gui.class.getResourceAsStream(resName);
			if (is == null) {
				gui.log("Error: Cannot load resource %1s", resName);
				return null;
			}
			File diskFile = new File(new File(workingDir()), path);
			diskFile.getParentFile().mkdirs(); 
			String diskPath = diskFile.toString();
			writer = new FileOutputStream(diskFile);
			IOUtils.copy(is, writer);
			gui.log("Extracted resource to %1s", diskPath);
			result = diskPath;
		} catch (IOException e) {
			gui.log("Failed to extract resource %1s", resName);
			// TODO Auto-generated catch block
			e.printStackTrace();
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
			String ipswDirName = String.format("ipsw_%1s_%1s", 
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
	
	static boolean _isArmV6;
	static boolean _isArmV6Inited = false;
	
	Hashtable<String, String> filePropsByName(String name)
	{
		Hashtable<String, String> props = new Hashtable<String, String>();
		String normalizedName = name.toLowerCase();
		boolean ios5 = (null != dict.objectForKey("ios5"));
		boolean ios3 = (null != dict.objectForKey("ios3"));
		String norPatch = "nor5.patch.json";
		String kernelPatch = "kernel5.patch.json";
		String wtfPatch = "wtf.patch.json";
	
		if (!ios5) {
			norPatch = device.isWtf() ? wtfPatch : device.isArmV6() ? "nor_armv6.patch.json" : "nor.patch.json";
			kernelPatch = device.isArmV6() ? ( ios3 ? "kernel3.patch.json": "kernel_armv6.patch.json" ) : "kernel.patch.json";
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
	
	static boolean getFileFromZip(String zipUrl, String zipPath, String downloadPath)
	{
		if (zipUrl.toLowerCase().startsWith("http:")) {
			return 0 == Jsyringe.download_file_from_zip(zipUrl, zipPath, downloadPath);
		} else {
			File zipFile = new File(new File(workingDir()), zipUrl);
			while (!zipFile.exists()) {
				gui.log("Apple doesn't allow %1s for download; please find it yourself and place in the %2s directory", zipUrl, workingDir());
				try {
					Thread.sleep(5* 1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}				
			}
			
			try {
				ZipFile zf = new ZipFile(zipFile);
				ZipEntry ze = zf.getEntry(zipPath);
				InputStream is = zf.getInputStream(ze);
				IOUtils.copy(is, new FileOutputStream(downloadPath));
				return true;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				gui.log("IOException unpacking %1s, check IPSW", zipPath);
				return false;
			}
		}
	}
	
	String downloadAndProcessFile(String zipPath) 
	{
		gui.log("Downloading %1s", zipPath);
		String finalPath = new File(new File(ipswDir()), zipPath).getPath();
		// Ensure directory exists
		File finalFile = new File(finalPath);
		if (finalFile.exists()) {
			gui.log("Skipping processing of %1s, file already exists!", finalPath);
			return finalPath;
		}
		finalFile.getParentFile().mkdirs(); 
		Hashtable<String, String>fileProps = filePropsByName(zipPath);
		boolean needsDecrypting = !fileProps.containsKey("passthrough");
		
		String downloadPath = finalPath;
		if (needsDecrypting)
			downloadPath = finalPath + ".orig";
		if (!getFileFromZip(ipswUrl, zipPath, downloadPath)) {
			gui.log("Download failed! %1s [%2s] -> %3s", ipswUrl, zipPath, downloadPath);
			return null;
		}
		gui.log("Downloaded to %1s", downloadPath);
		
		if (needsDecrypting) {
			String decryptedPath = finalPath + ".dec";
			if (!Jsyringe.process_img3_file(downloadPath, decryptedPath, null, 
					stringFromNsDict(dict, fileProps.get("iv")), 
					stringFromNsDict(dict, fileProps.get("key")))) {
				gui.log("Decryption failed");
				return null;
			}
			gui.log("Decrypted to %1s", decryptedPath);
			String patch = fileProps.get("patch");
			if (patch != null) {
				String patchedPath = decryptedPath + ".p";
				String patchJson = Background.getResourceFile(patch);
				if (patchJson == null) {
					gui.log("getResourceFile(%1s) failed, log a bug!", patch);
					return null;
				}
				if (!Jsyringe.fuzzy_patch(decryptedPath, patchedPath, patchJson, 80)) {
					gui.log("Patching failed");
					return null;
				}
				decryptedPath = patchedPath;
				gui.log("Patched to %1s", patchedPath);
			}
			if (fileProps.containsKey("ramdisk")) {
				String sshTarFile = Background.getResourceFile("ssh.tar");
				if (sshTarFile == null) {
					gui.log("getResourceFile(ssh.tar) failed, log a bug!");
					return null;
				}
				long extend;
				long tarLength = new File(sshTarFile).length();
				if (tarLength == 0) {
					gui.log("Can't get tar file size!");
					return null;
				}
				extend = (long)(1.05 * (double)(tarLength));
				if (!Jsyringe.add_ssh_to_ramdisk(decryptedPath, sshTarFile, extend)) {
					gui.log("Adding ssh to ramdisk failed!");
					return null;
				}
				gui.log("Added ssh.tar to the ramdisk");
			}
			if (!Jsyringe.process_img3_file(decryptedPath, finalPath, downloadPath, 
					stringFromNsDict(dict, fileProps.get("iv")),
					stringFromNsDict(dict, fileProps.get("key")))) {
				gui.log("Encryption failed");
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
				gui.log("wiki URL: %1s", url);
				dict = WebScraper.loadAndParseFirmwarePage(url);
				if (dict == null)
					continue;
				for (Iterator<String> it = WebScraper.displayFields.iterator(); it.hasNext(); ) { 
	    			String key = it.next();
	    			String value = dict.get(key);
	    			if (value != null) {
	    				gui.log("%1s\t: %2s", key, value);
	    			}
				}
				gui.log("Enough keys: %1s", WebScraper.hasEnoughKeys(dict) ? "YES" : "NO");
				
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
				gui.log("Added %1s!", dp.apName);				
			} else {
				++cSkipped;
				gui.log("Skipped %1s!", dp.apName);
			}
		}
		if (cSkipped != 0)
			return false;
		try {
			PropertyListParser.saveAsXML(plDict, new File("/tmp/all_keys.plist"));
			gui.log("Saved everything to file!");
			return true;
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		return false;
	}
	
	public void run()
	{
		try {
			//fetchKeysFromWiki();
			while (true) {
				Device d = s_queue.poll(1, TimeUnit.SECONDS);
				if (d != null)
					onDfuDeviceArrival(d);				
			}
		} catch (InterruptedException e) {
			gui.log("!! FAIL: Unhandled exception in background thread: %1s, %2s", e.toString(), e.getMessage());
		}
	}
	
	void onDfuDeviceArrival(Device dev) 
	{
		gui.log("DFU device '%1s' connected", dev.getName());
		if (dev.isUnsupported()) {
			gui.log("Ignoring unsupported device %1s", dev.getName());
			return;
		}
		if (this.device != null && this.device.getName().equals(dev.getName())) {
			gui.log("Ignoring same device %1s", dev.getName());
			return;
		}
		this.device = dev;
		prepareRamdiskForDevice();
	}
	
	void prepareRamdiskForDevice()
	{	
		_ipswDir = null;
		String keyFileName = Background.getResourceFile("all_keys.plist");
		NSDictionary plDict;
		try {
			plDict = (NSDictionary)PropertyListParser.parse(new File(keyFileName));
		} catch (Exception e1) {
			gui.log("Cannot load all_keys.plist from resources (%1s); bailing !", e1.getMessage());
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return;
		}
		dict = (NSDictionary)plDict.objectForKey(device.getAp());

		gui.log("Working dir set to %1s", workingDir());
		
		ipswUrl = stringFromNsDict(dict, WebScraper.downloadUrl);
		
		gui.log("IPSW at %1s", ipswUrl);
		
		if (device.isWtfStub()) {
			dict.put(WebScraper.device, "dfu8900");
		}
		
		String restorePlistFile = downloadAndProcessFile("Restore.plist");
		if (restorePlistFile == null) {
			gui.log("Restore.plist download failed!");
			return;
		}
		gui.log("Restore.plist downloaded to %1s", restorePlistFile);
		
		gui.log("Parsing Restore.plist..");
		
		File restorePlist = new File(restorePlistFile);
		
		NSDictionary restoreDict = null;
		try {
			restoreDict = (NSDictionary)PropertyListParser.parse(restorePlist);
		} catch (Exception e) {
			gui.log("Can't parse Restore.plist, bailing!");
			e.printStackTrace();
			return;
		}
		
		String iosVersion = stringFromNsDict(restoreDict, "ProductVersion");
		String iosVerMajor = iosVersion.substring(0, 1);
		dict.put("ios", iosVerMajor);
		dict.put("ios" + iosVerMajor, "yes"); //ios5, ios4, ios3
		
		NSDictionary kcByTargetDict = (NSDictionary)restoreDict.objectForKey("KernelCachesByTarget");
		NSDictionary kcDict = null;
		if (kcByTargetDict != null) {
			String modelNoAp = device.getAp().replaceAll("ap$", "");
			kcDict = (NSDictionary)kcByTargetDict.objectForKey(modelNoAp);
		} else {
			kcDict = (NSDictionary)restoreDict.objectForKey("RestoreKernelCaches");
		}
		String kernelName = stringFromNsDict(kcDict, "Release");		
		gui.log("Kernel file: %1s", kernelName);
		
		NSDictionary ramdisksDict = (NSDictionary)restoreDict.objectForKey("RestoreRamDisks");
		String ramdiskName = stringFromNsDict(ramdisksDict, "User");		
		gui.log("Restore ramdisk file: %1s", ramdiskName);
		
		String dfuFolder = "Firmware/dfu/";
		String ibssName = String.format("iBSS.%1s.RELEASE.dfu", device.getAp());
		String ibssPath = dfuFolder.concat(ibssName);
		
		if (!device.isWtfStub()) {
			String ibssFile = downloadAndProcessFile(ibssPath);
		
			if (ibssFile == null) {
				gui.log("iBSS download failed!");
				return;
			}
			gui.log("iBSS prepared at %1s", ibssFile);
		}
		
		String ibecFile = null;
		if (null != dict.objectForKey("ios5")) {
			String ibecName = String.format("iBEC.%1s.RELEASE.dfu", device.getAp());
			String ibecPath =  dfuFolder.concat(ibecName);
			
			ibecFile = downloadAndProcessFile(ibecPath);
			
			if (ibecFile == null) {
				gui.log("iBEC download failed!");
				return;
			}
			gui.log("iBEC prepared at %1s", ibecFile);
		}

		String wtf8900File = null;
		String wtfModelFile = null;
		if (device.isWtf() || device.isWtfStub()) {
			String wtf8900Name = "WTF.s5l8900xall.RELEASE.dfu";
			String wtf8900Path =  dfuFolder.concat(wtf8900Name);
			String wtfModelName = String.format("WTF.%1s.RELEASE.dfu", device.getAp());;
			String wtfModelPath =  dfuFolder.concat(wtfModelName);
			
			wtf8900File = downloadAndProcessFile(wtf8900Path);
			
			if (wtf8900File == null) {
				gui.log("WTF.s5l8900xall download failed!");
				return;
			}
			gui.log("WTF.s5l8900xall prepared at %1s", wtf8900File);
		
			if (!device.isWtfStub()) {
				wtfModelFile = downloadAndProcessFile(wtfModelPath);
					
				if (wtfModelFile == null) {
					gui.log("%1s download failed!", wtfModelName);
					return;
				}
				
				gui.log("%1s prepared at %2s", wtfModelName, wtfModelFile);
			}
		}
		
		if (!device.isWtfStub()) {
			String deviceTreeName =  String.format("DeviceTree.%1s.img3", device.getAp());
		
			String deviceTreePath = String.format("Firmware/all_flash/all_flash.%1s.production/%2s", device.getAp(), deviceTreeName);
		
			String deviceTreeFile = downloadAndProcessFile(deviceTreePath);
		
			if (deviceTreeFile == null) {
				gui.log("Device tree download failed!");
				return;
			}
			gui.log("Device tree prepared at %1s", deviceTreeFile);
		

			String manifestPath = String.format("Firmware/all_flash/all_flash.%1s.production/manifest", device.getAp());
		
			String manifestFile = downloadAndProcessFile(manifestPath);
		
			if (manifestFile == null) {
				gui.log("Manifest download failed!");
				return;
			}
		
			String kernelFile = downloadAndProcessFile(kernelName);
			
			if (kernelFile == null) {
				gui.log("Kernel download failed!");
				return;
			}
	
			gui.log("Kernel prepared at %1s", kernelFile);
			
			String ramdiskFile = downloadAndProcessFile(ramdiskName);
			
			if (ramdiskFile == null) {
				gui.log("Ramdisk download failed!");
				return;
			}
			gui.log("Ramdisk prepared at %1s", ramdiskFile);
			
	
			if (!device.isWtf()) {
				if (0 != Jsyringe.exploit()) {
					gui.log("Exploiting the device failed!");
					return;
				}
				gui.log("Exploit sent!");
			}
		} // endif (!device.isWtfStub()) 
		if (!device.isWtfStub()) 
			gui.log("Trying to load the ramdisk..");
		else
			gui.log("Trying to pwn 8900 DFU mode..");
			
		if (!Jsyringe.restore_bundle(ipswDir())) {
			gui.log("Failed to use iTunes API to load the ramdisk!");
			return;
		}
		if (!device.isWtfStub()) 
			gui.log("Ramdisk load started..");
		else
			gui.log("8900 exploit load started..");
	}
}
