public class MobileDevice implements Runnable {
	static MobileDevice s_mobileDevice;
	Thread thread;
	boolean connected = false;
		
	static {
		s_mobileDevice = new MobileDevice();
	};

	MobileDevice()
	{
		thread = new Thread(this, "MobileDevice");
	}
	
	public static void start() {
		if (s_mobileDevice.thread.getState() == Thread.State.NEW) {
			s_mobileDevice.thread.start();
		}
	}

	enum CallbackType {
		DfuConnect,
		DfuDisconnect,
		RecoveryConnect,
		RecoveryDisconnect,
		MuxConnect,
		MuxDisconnect,
		Unknown
	}
	
	CallbackType getCallbackType(int type)
	{
		switch (type) {
		case 0:
			return CallbackType.DfuConnect;
		case 1:
			return CallbackType.DfuDisconnect;
		case 2:
			return CallbackType.RecoveryConnect;
		case 3:
			return CallbackType.RecoveryDisconnect;
		case 4:
			return CallbackType.MuxConnect;
		case 5:
			return CallbackType.MuxDisconnect;
		default:
			return CallbackType.Unknown;
		}		
	}
	
	void callback(int intType, int productId, int productType) {
		CallbackType type = getCallbackType(intType);
		String eventName = type.name();
		gui.trace("MobileDevice event: %1$s, %2$x, %3$x", eventName, productId, productType);
		switch (type) {
			case DfuConnect:
				Background.getQueue().add(new Device(productId, productType));
				break;
			case RecoveryDisconnect:
				if (Background.ramdiskSent())
					gui.log("Almost there..");
				break;
			case MuxConnect:
				if (!connected && Background.ramdiskSent()) {
					connected = true;
					gui.success("\nSuccess!\nConnect to localhost on port 2022 with your favorite SSH client!");	
					gui.log(gui.MessageStyle.Important, "\n login: root\n password: alpine");	
				}
				break;
		}
	}
	
	public void run() {
		try {
			Jsyringe.runMobileDeviceThread(this);
			gui.error("MobileDevice thread proc returned! It really should not!");
		} catch (Exception e) {
			gui.error("!! FAIL: Unhandled exception in MobileDevice thread!");
			gui.exc(e);			
		}
	}
}
