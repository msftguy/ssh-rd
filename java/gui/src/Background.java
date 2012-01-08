import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;

import com.dd.plist.*;
import org.apache.commons.io.*;

public class Background implements Runnable {
	static Background s_background;
	String ipswUrl = null;
	NSDictionary dict = null;
	
	Thread thread;
	
	// Callbacks
	//Runnable onProgress;
	
	static {
		s_background = new Background();
	};

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
					//Files.createTempDirectory("ssh_rd_dir");
		}
		return _workingDir.toString();
	}
	
	static String _ipswDir = null;
	
	static String ipswDir()
	{
		if (_ipswDir == null) {
			String ipswDirName = String.format("ipsw_%1s_%1s", 
					stringFromNsDict(s_background.dict, "device"), 
					stringFromNsDict(s_background.dict, "build")); 
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
	
	boolean isArmV6()
	{
		if (!_isArmV6Inited) {
			ArrayList<String> armV6models = new ArrayList<String>();
			armV6models.add("iphone11");
			armV6models.add("iphone12");
			armV6models.add("ipod11");
			armV6models.add("ipod21");
			String model = stringFromNsDict(dict, WebScraper.device);
			_isArmV6 = armV6models.contains(model);
			_isArmV6Inited = true;
		}
		return _isArmV6;
	}
	
	Hashtable<String, String> filePropsByName(String name)
	{
		Hashtable<String, String> props = new Hashtable<String, String>();
		String normalizedName = name.toLowerCase();
		boolean ios5 = (null != dict.objectForKey("ios5"));
		String norPatch = "nor5.patch.json";
		String kernelPatch = "kernel5.patch.json";
		if (!ios5) {
			norPatch = isArmV6() ? "nor_armv6.patch.json" : "nor.patch.json";
			kernelPatch = isArmV6() ? "kernel_armv6.patch.json" : "kernel.patch.json";
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
		} else { // manifest, device tree, Restore.plist
			props.put("passthrough", "yes");
		}
		return props;
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
		if (0 != Jsyringe.download_file_from_zip(ipswUrl, zipPath, downloadPath)) {
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
				if (!Jsyringe.fuzzy_patch(decryptedPath, patchedPath, patchJson, 80)) {
					gui.log("Patching failed");
					return null;
				}
				decryptedPath = patchedPath;
				gui.log("Patched to %1s", patchedPath);
			}
			if (fileProps.containsKey("ramdisk")) {
				String sshTarFile = Background.getResourceFile("ssh.tar");
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
		for (String m : Device.supportedDevices) {
			ArrayList<String> urls = WebScraper.getFirmwareUrls(m);
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
				plDict.put(m, nsDict);
				gui.log("Added %1s!", m);				
			} else {
				++cSkipped;
				gui.log("Skipped %1s!", m);
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
		//try {
			//fetchKeysFromWiki();
			doStuff();
//		} catch (Exception e) {
//			gui.log("!! FAIL: Unhandled exception in background thread: %1s", e.getMessage());
//		}
	}
	
	void doStuff()
	{		
		String model = Device.getModel();
		gui.log("Device %1s connected", model);

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
		dict = (NSDictionary)plDict.objectForKey(model);

		gui.log("Working dir set to %1s", workingDir());
		
		ipswUrl = stringFromNsDict(dict, WebScraper.downloadUrl);
		
		gui.log("IPSW at %1s", ipswUrl);
		
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
		if (iosVersion.startsWith("5"))
			dict.put("ios5", "yes");
		
		NSDictionary kcByTargetDict = (NSDictionary)restoreDict.objectForKey("KernelCachesByTarget");
		NSDictionary kcDict = null;
		if (kcByTargetDict != null) {
			String modelNoAp = model.replaceAll("ap$", "");
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
		String ibssName = String.format("iBSS.%1s.RELEASE.dfu", model);
		String ibssPath = dfuFolder.concat(ibssName);
		
		String ibssFile = downloadAndProcessFile(ibssPath);
		
		if (ibssFile == null) {
			gui.log("iBSS download failed!");
			return;
		}
		
		String ibecFile = null;
		if (null != dict.objectForKey("ios5")) {
			String ibecName = String.format("iBEC.%1s.RELEASE.dfu", model);
			String ibecPath =  dfuFolder.concat(ibecName);
			
			ibecFile = downloadAndProcessFile(ibecPath);
			
			if (ibecFile == null) {
				gui.log("iBEC download failed!");
				return;
			}
		}

		String deviceTreeName =  String.format("DeviceTree.%1s.img3", model);
		String deviceTreePath = String.format("Firmware/all_flash/all_flash.%1s.production/%2s", model, deviceTreeName);
		
		String deviceTreeFile = downloadAndProcessFile(deviceTreePath);
		
		if (deviceTreeFile == null) {
			gui.log("Device tree download failed!");
			return;
		}

		String manifestPath = String.format("Firmware/all_flash/all_flash.%1s.production/manifest", model);
		
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
		

		if (0 != Jsyringe.exploit()) {
			gui.log("Exploiting the device failed!");
			return;
		}
		gui.log("Exploit sent!");
		
		
		if (!Jsyringe.restore_bundle(ipswDir())) {
			gui.log("Failed to use iTunes API to load the ramdisk!");
			return;
		}
		gui.log("Initiated ramdisk load!");
	}
}
