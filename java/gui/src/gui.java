import java.awt.*;
import java.awt.event.*;

public class gui extends Frame{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	static TextArea log;
	Handler handler;
	//static gui _gui = null;
	
	public static void log(String format, Object... args)
	{
		final StringBuilder sb = new StringBuilder(String.format(format, args));
		if (sb.length() != 0) {
			if (sb.charAt(sb.length() - 1) != '\n') {
				sb.append('\n');
			}
		}
//		display.asyncExec(new Runnable() {
//			@Override
//			public void run() {
				log.append(sb.toString());
//			}
//		});
	}
	
	String getVersion()
	{
		return "08-01-2012 git rev-01";
	}
	
	void about()
	{
        log(" ");
        log(" SSH ramdisk maker & loader, version %1s", getVersion());
        log("Made possible thanks to Camilo Rodrigues (@Allpluscomputer)");
        log("Including xpwn source code by the Dev Team and planetbeing");
        log("Including syringe source code by Chronic-Dev and posixninja");
        log("syringe exploits by pod2g, geohot & posixninja");
        log("Report bugs to msft.guy<msft.guy@gmail.com> (@msft_guy)");
        log(" ");
        log(" ");
	
	}
	
	gui() {
		//	    display = new Display ();
//	    Shell shell = new Shell (display);
        GridLayout layout = new GridLayout(1,1);
        // Optionally set layout fields.
        // Set the layout into the composite.
        setLayout(layout);
        //shell.setLayout(layout);
        
        log = new TextArea();
        add(log);
//        GridData gridData = new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_FILL);
//		gridData.grabExcessHorizontalSpace = true;
//		gridData.grabExcessVerticalSpace = true;
//		gridData.minimumHeight = 200;
//		gridData.minimumWidth = 300;
		//log.setLayoutData(gridData);
        log.setEditable(false);
        about();
        log("Connect a device in DFU mode");
        if (Jsyringe.init()) {
        
	        //Device.setDisplay(display);
	        Device.setOnConnected(new Runnable() {
				@Override
				public void run() {
					Background.start();
				}
			});
	        
	        Device.start();

        }
        handler = new Handler();
        addWindowListener (handler);
        setSize(500, 400);
        setVisible(true);
	}
	
	public static void main (String [] args) {
		new gui();
	}
}

class Handler extends WindowAdapter {
	public void windowClosing (WindowEvent event) {
        System.exit (0);
    }
}

