import java.net.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

import org.jsoup.*;
import org.jsoup.nodes.*;

public class WebScraper {
	static final int Timeout = 30000;
	public static final String device = "device";
	public static final String build = "build";
	public static final String ramdiskName = "restoredmg";
	public static final String ramdiskNotEncrypted = "ramdisknotencrypted";
	public static final String ramdiskIV = "restoreiv";
	public static final String ramdiskKey = "restorekey";
	//public static final String kernelName = ""; // actually this is in the Restore.plist
	public static final String kernelIV = "KernelcacheIV";
	public static final String kernelKey = "KernelcacheKey";
	public static final String ibssIV = "iBSSIV";
	public static final String ibssKey = "iBSSKey";
	public static final String ibecIV = "iBECIV";
	public static final String ibecKey = "iBECKey";
	public static final String downloadUrl = "downloadurl";
	
	public Hashtable<String,String> dict;

	public static ArrayList<String> displayFields = new ArrayList<String>(Arrays.asList(
			device,
			build,
			ramdiskName,
			ramdiskNotEncrypted,
			ramdiskIV,
			ramdiskKey,
			kernelIV,
			kernelKey,
			ibssIV,
			ibssKey,
			ibecIV,
			ibecKey,
			downloadUrl));

	
	static final Pattern fwTablePattern = Pattern.compile("\\| \\[\\[([^|]*)\\|[^\\]]*\\]\\](?:\\n|\\r)\\| (?:\\[[^\\s]*Restore.ipsw [^\\s\\]]*\\])");
	
	static ArrayList<String> extractFirmwarePageUrlsFromTable(String wikiSource)
	{
		Matcher m = fwTablePattern.matcher(wikiSource);		
		ArrayList<String> result = new ArrayList<String>();
		while (m.find()) {
			String pageName = m.group(1);
			if (pageName == null || pageName.trim().length() == 0)
				continue;

			String pageNameUnderscored = pageName.replace(' ', '_');
			result.add(String.format("http://theiphonewiki.com/wiki/index.php?title=%1s", pageNameUnderscored));
		}
		return result;
	}	
	
	public static ArrayList<String> getFirmwareUrls(String deviceId)
	{
		HashMap<String, String> categoryNameFromDevId = new HashMap<String, String>();
		categoryNameFromDevId.put("k48ap", "iPad");
		categoryNameFromDevId.put("k66ap", "Apple_TV_2G");
		categoryNameFromDevId.put("m68ap", "iPhone");
		categoryNameFromDevId.put("n82ap", "iPhone_3G");
		categoryNameFromDevId.put("n88ap", "iPhone_3GS");
		categoryNameFromDevId.put("n90ap", "iPhone_4_GSM");
		categoryNameFromDevId.put("n92ap", "iPhone_4_CDMA");
		categoryNameFromDevId.put("n45ap", "iPod_touch");
		categoryNameFromDevId.put("n72ap", "iPod_touch_2G");
		categoryNameFromDevId.put("n18ap", "iPod_touch_3G");
		categoryNameFromDevId.put("n81ap", "iPod_touch_4G");
		
		String categoryUrl = String.format("http://theiphonewiki.com/wiki/index.php?title=Firmware/%1s", categoryNameFromDevId.get(deviceId));
		String wikiSource = wikiMarkupForPage(categoryUrl);
		if (wikiSource == null)
			return null;
		return extractFirmwarePageUrlsFromTable(wikiSource);
	}
	
	static final Pattern pattern = Pattern.compile("\\| (\\w+)\\s+= ([^\\s]+)\\s*");
	
	static Hashtable<String, String> parseFirmwarePage(String wikiSource)
	{
		Matcher m = pattern.matcher(wikiSource);
		Hashtable<String, String> dict = new Hashtable<String, String>();
		while (m.find()) {
			String key = m.group(1);
			if (key == null || 
					key.trim().length() == 0 ||
					!displayFields.contains(key)
					)
			{
				continue;
			}
			String val = m.group(2);
			if (val != null && 
					val.trim().length() != 0 && 
					!val.toLowerCase().contains("todo")
					) 
			{
				dict.put(m.group(1), val);
			}
		}
		return dict;
	}
	
	static String wikiMarkupForPage(String url)
	{
		final int retries = 3;
		final int retryDelay = 30;
		for (int i=0; i < retries; ++i) {
			if (i != 0) {
				gui.log("The iPhone Wiki seems a bit down; retrying in %1is..", retryDelay);
				try {
					Thread.sleep(retryDelay * 1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			try {
				Document doc = Jsoup.connect(url + "&action=edit").timeout(WebScraper.Timeout).get();
				Element wikiSourceElement = doc.getElementById("wpTextbox1");
				return ((TextNode)wikiSourceElement.childNodes().get(0)).getWholeText();
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return null;
	}

	public static Hashtable<String, String> loadAndParseFirmwarePage(String url)
	{
		String wikiSource = wikiMarkupForPage(url);
		if (wikiSource == null)
			return null;
		return parseFirmwarePage(wikiSource);
	}
	
	public static String modelNameFromAp(String ap)
	{
		HashMap<String, String> map = new HashMap<String, String>();
		map.put("k48ap", "iPad1,1");
		map.put("k66ap", "AppleTV2,1");
		map.put("m68ap", "iPhone1,1");
		map.put("n82ap", "iPhone1,2");
		map.put("n88ap", "iPhone2,1");
		map.put("n90ap", "iPhone3,1");
		map.put("n92ap", "iPhone3,3");
		map.put("n94ap", "iPhone4,1");
		map.put("n45ap", "iPod1,1");
		map.put("n72ap", "iPod2,1");
		map.put("n18ap", "iPod3,1");
		map.put("n81ap", "iPod3,1");
		
		return map.get(ap);
	}
	
	public static boolean hasEnoughKeys(Hashtable<String, String> dict)
	{
		String[] preImg3DfuDevices = new String[] {"iphone11", "iphone12", "ipod11"};
		String deviceCode = dict.get(WebScraper.device);
		if (deviceCode == null)
			return false;
		
		boolean preImg3Device = 
				Arrays.asList(preImg3DfuDevices).contains(deviceCode);
		boolean canDecryptRamdisk = 
				dict.get(WebScraper.ramdiskNotEncrypted) != null ||
				(
						dict.get(WebScraper.ramdiskIV) != null &&
						dict.get(WebScraper.ramdiskKey) != null
				);
		boolean canDecryptDfu = 
			dict.get(WebScraper.ibecIV) != null &&
			dict.get(WebScraper.ibecKey) != null &&
			dict.get(WebScraper.ibssIV) != null &&
			dict.get(WebScraper.ibssKey) != null;
		
		canDecryptDfu |= preImg3Device;
		
		boolean canDecryptKernel =
				dict.get(WebScraper.kernelIV) != null &&
				dict.get(WebScraper.kernelKey) != null;
		boolean hasDownloadUrl =
				dict.get(WebScraper.downloadUrl) != null;
		
		return canDecryptRamdisk && canDecryptDfu && canDecryptKernel && hasDownloadUrl;
	}
}
