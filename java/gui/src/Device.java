import java.util.*;

public final class Device {
	int productType;
	int productId;
	DeviceProps props;
	static DeviceProps unsupportedDevice = new DeviceProps("UNSUPPORTED", "???ap", 0x0, 0x0);
	
	static ArrayList<DeviceProps> supportedDevices;
		
	static {
		supportedDevices = new ArrayList<DeviceProps>();
		supportedDevices.add(new DeviceProps("DFU Mode S5L8900 Device", "m68ap", 0x8900, 0x0, true));
		supportedDevices.add(new DeviceProps("iPhone 2G", "m68ap", 0x8900, 0x1100));
		supportedDevices.add(new DeviceProps("iPhone 3G", "n82ap", 0x8900, 0x3100));
		supportedDevices.add(new DeviceProps("iPhone 3GS", "n88ap", 0x8920, 0x8920));
		supportedDevices.add(new DeviceProps("iPhone 4 (GSM)", "n90ap", 0x8930, 0x8930));
		supportedDevices.add(new DeviceProps("iPhone 4 (CDMA)", "n92ap", 0x8930, 0x6008930));
		supportedDevices.add(new DeviceProps("iPod Touch 1G", "n45ap", 0x8900, 0x2100));
		supportedDevices.add(new DeviceProps("iPod Touch 2G", "n72ap", 0x8720, 0x8720));
		supportedDevices.add(new DeviceProps("iPod Touch 3G", "n18ap", 0x8922, 0x2008922));
		supportedDevices.add(new DeviceProps("iPod Touch 4G", "n81ap", 0x8930, 0x8008930));
		supportedDevices.add(new DeviceProps("iPad 1G", "k48ap", 0x8930, 0x2008930));
		supportedDevices.add(new DeviceProps("Apple TV 2G", "k66ap", 0x8930, 0x10008930));
	};
	
	public String getAp()
	{
		return props.apName;
	}
	
	public String getName()
	{
		return props.displayName;
	}
	
	public boolean isArmV6()
	{
		return props.productChip <= 0x8900;
	}
	
	public boolean isWtf()
	{
		return props.productChip == 0x8900;
	}
	
	public boolean isWtfStub()
	{
		return props.productCode == 0;
	}
	
	public boolean isUnsupported()
	{
		return props == unsupportedDevice;
	}
	
	static ArrayList<DeviceProps> __TEST__getSupportedDevices()
	{
		return supportedDevices;
	}
	
	public Device(int productId, int productType)
	{
		this.productId = productId;
		this.productType = productType;
		int dfuCheck = (productType >> 16) & 0xFFFF;
		int chipId;
		if (dfuCheck == 0x1222 || dfuCheck == 0x1280) {
			productType = productType & 0xffff;
			chipId = 0x8900;
		} else 
			chipId = productType & 0xffff;
		for (DeviceProps p : supportedDevices) {
			if (p.productChip == chipId && p.productCode == productType) {
				props = p;
				break;
			}
		}
		if (props == null) {
			props = unsupportedDevice;
		}
	}
}
