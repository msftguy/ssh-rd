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
	Hashtable<String, String> dict = null;
	
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
			_ipswDir = new File(new File(workingDir()), "ipsw").getPath();
	 	}
		return _ipswDir.toString();
	}
	
	Hashtable<String, String> filePropsByName(String name)
	{
		Hashtable<String, String> props = new Hashtable<String, String>();
		String normalizedName = name.toLowerCase();
		boolean ios5 = (null != dict.get("ios5"));
		if (normalizedName.contains("kernelcache")) {
			props.put("iv", WebScraper.kernelIV);
			props.put("key", WebScraper.kernelKey);
			props.put("patch", ios5 ? "kernel5.patch.json" : "kernel.patch.json");
		} else if (normalizedName.contains("ibss")) {
			props.put("iv", WebScraper.ibssIV);
			props.put("key", WebScraper.ibssKey);
			props.put("patch", ios5 ? "nor5.patch.json" : "nor.patch.json");
		} else if (normalizedName.contains("ibec")) {
			props.put("iv", WebScraper.ibecIV);
			props.put("key", WebScraper.ibecKey);	
			props.put("patch", ios5 ? "nor5.patch.json" : "nor.patch.json");
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
			if (!Jsyringe.process_img3_file(downloadPath, decryptedPath, null, dict.get(fileProps.get("iv")), dict.get(fileProps.get("key")))) {
				gui.log("Decryption failed");
				return null;
			}
			String patch = fileProps.get("patch");
			if (patch != null) {
				String patchedPath = decryptedPath + ".p";
				String patchJson = Background.getResourceFile(patch);
				if (!Jsyringe.fuzzy_patch(decryptedPath, patchedPath, patchJson, 80)) {
					gui.log("Patching failed");
					return null;
				}
				decryptedPath = patchedPath;
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
			}
			if (!Jsyringe.process_img3_file(decryptedPath, finalPath, downloadPath, dict.get(fileProps.get("iv")), dict.get(fileProps.get("key")))) {
				gui.log("Encryption failed");
				return null;
			}
		}
		return finalPath;
	}
	
	public void run()
	{		
		String model = Device.getModel();
		gui.log("Device %1s connected", model);
		ArrayList<String> urls = WebScraper.getFirmwareUrls(model);
		boolean ok = false;
		dict = null;
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
		if (!ok) {
			gui.log("Can't get enough stuff from The iPhone Wiki; check your internet connection or report a bug!");
			return;
		}

		gui.log("Working dir set to %1s", workingDir());
		
		ipswUrl = dict.get(WebScraper.downloadUrl);
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
		
		String iosVersion = ((NSString)restoreDict.objectForKey("ProductVersion")).toString();
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
		String kernelName = ((NSString)kcDict.objectForKey("Release")).toString();		
		gui.log("Kernel file: %1s", kernelName);
		
		NSDictionary ramdisksDict = (NSDictionary)restoreDict.objectForKey("RestoreRamDisks");
		String ramdiskName = ((NSString)ramdisksDict.objectForKey("User")).toString();		
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
		if (dict.containsKey("ios5")) {
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
		
		String ramdiskFile = downloadAndProcessFile(ramdiskName);
		
		if (ramdiskFile == null) {
			gui.log("Ramdisk download failed!");
			return;
		}
		gui.log("Ramdisk downloaded to %1s", kernelFile);
		

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
