
public class MobileDevice implements Runnable {
	static MobileDevice s_mobileDevice;
	Thread thread;
	boolean muxThreadStarted = false;
	
	// Callbacks
	//Runnable onProgress;
	
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

	void callback(int type, int productId, int productType) {
		String eventName = null;
		switch (type) {
			case 0:
				eventName = "DfuConnect";
				Background.getQueue().add(new Device(productId, productType));
				break;
			case 1:
				eventName = "DfuDisconnect";
				break;
			case 2:
				eventName = "RecoveryConnect";
				break;
			case 3:
				eventName = "RecoveryDisconnect";
				if (!muxThreadStarted) {
					Jsyringe.startMuxThread(22, 2022);
					gui.log("Connect to localhost:2022");
				}
				break;
			case 4:
				eventName = "MuxConnect";
				break;
			case 5:
				eventName = "MuxDisconnect";
				break;
			default:
				eventName = "Unknown!";
				break;
		}
		gui.log("MobileDevice event: %1s, %2x, %3x", eventName, productId, productType);
	}
	
	public void run() {
		Jsyringe.runMobileDeviceThread(this);
	}
}
