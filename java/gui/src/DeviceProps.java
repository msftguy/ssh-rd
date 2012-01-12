
public class DeviceProps {
	public final String displayName;
	public final String apName;
	public final int productChip;
	public final int productCode;
	public final boolean isDfuStub; 

	public DeviceProps(String displayName, String apName, int productChip, int productCode) 
	{
		this.displayName = displayName; 
		this.apName = apName;
		this.productChip = productChip;
		this.productCode = productCode;
		this.isDfuStub = false;			
	}

	public DeviceProps(String displayName, String apName, int productChip, int productCode, boolean isDfuStub) 
	{
		this.displayName = displayName; 
		this.apName = apName;
		this.productChip = productChip;
		this.productCode = productCode;
		this.isDfuStub = isDfuStub;
	}
	
	public boolean isSupported()
	{
		return productChip != 0;
	}
}
