public class Device implements Runnable {
	static Device s_device;
	
	Thread thread;
	
	// Callbacks
	Runnable onConnected;
	Runnable onProgress;
	
	String model;
	
	static {
		s_device = new Device();
	};

	Device()
	{
		thread = new Thread(this, "Device");
	}
	
	public static void start() {
		if (s_device.thread.getState() == Thread.State.NEW) {
			s_device.thread.start();
		}
	}
	
	public static String getModel()
	{
		return s_device.model;
	}
	
	public static void setOnConnected(Runnable r)
	{
		s_device.onConnected = r;
	}
	
	@Override
	public void run() {
		while (!Jsyringe.wait_for_connect()) {
			try {
				Thread.sleep(500);
				gui.log("Waiting...");
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		model = Jsyringe.get_device_model();
		onConnected.run();
	}
}
