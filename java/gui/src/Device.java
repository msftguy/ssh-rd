import java.util.*;

public class Device implements Runnable {
	static Device s_device;
	static ArrayList<String> supportedDevices;
	
	Thread thread;
	
	// Callbacks
	Runnable onConnected;
	Runnable onProgress;
	
	String model;
	
	static {
		supportedDevices = new ArrayList<String>();
		supportedDevices.add("k66ap");
		supportedDevices.add("n81ap");
		supportedDevices.add("n90ap");
		supportedDevices.add("k48ap");
		supportedDevices.add("n18ap");
		supportedDevices.add("n88ap");
		supportedDevices.add("n72ap");
//		supportedDevices.add("n82ap");
//		supportedDevices.add("n45ap");
//		supportedDevices.add("m68ap");
		
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

		if (!supportedDevices.contains(model)) {
			gui.log("Sorry, your device (%1s) is not supported", model);
		} else {
			onConnected.run();
		}
	}
}
