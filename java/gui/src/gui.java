import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.text.*;

import org.apache.commons.cli.*;

public class gui extends Frame {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	static JTextPane log;
	Handler handler;
	private CommandLine _cmd;
	private static gui s_gui;
	
	public static boolean getTestOption() {
		return s_gui._cmd.hasOption('t');
	}

	public static boolean getFetchOption() {
		return s_gui._cmd.hasOption('f');
	}

	public enum MessageStyle {
		Normal,
		Important,
		Error,
		Warning,
		Trace,
		Success
	}
	
	class logEntry{
		public String message;
		public MessageStyle style;
	} 
	
	String getVersion()
	{
		return "29-06-2013 git rev-04b";
	}

	public static void log(String format, Object... args)
	{
		log(MessageStyle.Normal, format, args);		
	}
	
	public static void trace(String format, Object... args)
	{
		log(MessageStyle.Trace, format, args);				
	}

	public static void warn(String format, Object... args)
	{
		log(MessageStyle.Warning, format, args);		
	}

	public static void error(String format, Object... args)
	{
		log(MessageStyle.Error, format, args);		
	}
	
	public static void success(String format, Object... args)
	{
		log(MessageStyle.Success, format, args);		
	}

	static void throwableErr(Throwable t) 
	{
		String typeString = "Throwable";
		if (t instanceof Exception)
			typeString = "Exception";
		else if (t instanceof Error) 
			typeString = "Error";
			
		StringBuilder sb = new StringBuilder(String.format("%s %s\n", typeString, t.toString()));
		for( StackTraceElement ste : t.getStackTrace() ) {
			sb.append(ste);
			sb.append("\n");
		}
		error(sb.toString());		
	}
	
	public static void exc(Exception e)
	{
		throwableErr(e);
	}
	
	public static void err(Error err)
	{
		throwableErr(err);
	}

	static void appendStyledStringMain(MessageStyle style, String s)
	{
		AttributeSet aset = attrsFromStyle(style);

		int len = log.getDocument().getLength(); // same value as getText().length();
	    log.setCaretPosition(len);  // place caret at the end (with no selection)
	    StyledDocument doc = log.getStyledDocument();
	    try {
			doc.insertString(len, s, aset);
		} catch (BadLocationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} // there is no selection, so inserts at caret
	}
	
	static void appendStyledString(final MessageStyle style, final String s)
	{
		
		SwingUtilities.invokeLater(new Runnable() {
		    public void run() { 
		    	gui.appendStyledStringMain(style, s); 
		    }
		});
	}
	
	static AttributeSet attrsFromStyle(MessageStyle s) {
	    StyleContext sc = StyleContext.getDefaultStyleContext();
	    AttributeSet aset = SimpleAttributeSet.EMPTY;
	    
	    Color c = null;
		switch (s) {
		case Error:
			c = Color.red;
			break;
		case Warning:
			c = Color.pink;
			break;
		case Trace:
			c = Color.lightGray;
			break;
		case Normal:
			c = Color.black;
			break;
		case Important:
			c = Color.black;
		    aset = sc.addAttribute(aset, StyleConstants.Bold, true);
			break;
		case Success:
			c = new Color(0, 0x80, 0); // dark green-ish
		    aset = sc.addAttribute(aset, StyleConstants.Bold, true);
			break;
		}
	    aset = sc.addAttribute(aset, StyleConstants.Foreground, c);
		
		return aset;
	}

	public static void log(MessageStyle style, String format, Object... args)
	{
		final StringBuilder sb = new StringBuilder(String.format(format, args));
		if (sb.length() != 0) {
			if (sb.charAt(sb.length() - 1) != '\n') {
				sb.append('\n');
			}
		}
		appendStyledString(style, sb.toString());
	}
		
	void about()
	{
        log(" ");
        log(" SSH ramdisk maker & loader, version %s", getVersion());
        log("Made possible thanks to Camilo Rodrigues (@Allpluscomputer)");
        log("Including xpwn source code by the Dev Team and planetbeing");
        log("Including syringe source code by Chronic-Dev and posixninja");
        log("syringe exploits by pod2g, geohot & posixninja");
        log("Special thanks to iH8sn0w");
        log("device-infos source: iphone-dataprotection");
        log("Report bugs to msft.guy<msft.guy@gmail.com> (@msft_guy)");
        log(" ");
        log(" ");
	}
	
	gui(CommandLine cmd) 
	{
		s_gui = this;
		this._cmd = cmd;
		SwingUtilities.invokeLater(new Runnable() {//help prevent some weird Swing-related deadlocks
		    public void run() { 
		    	guiInit();
		    }
		});
	}
	
	void guiInit() 
	{
		GridLayout layout = new GridLayout(1,1);
        setLayout(layout);
                
        log = new JTextPane();
        JScrollPane scrollPane = new JScrollPane(log);
        scrollPane.setVerticalScrollBarPolicy(
                        JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        add(scrollPane);
        
        log.setEditable(false);

        about();
        
        if (!Jsyringe.init()) {
        	error("\n INIT FAILED (Jsyringe)!");
        } else if (!Jsyringe.startMuxThread(22, 2022)) {
           	error("\n INIT FAILED (mux thread)!"); 
           	log(MessageStyle.Important, "Possible causes:\n    iTunes 9 or newer is NOT installed.\n    Could not bind to the port 2022 (make sure only one instance is running!");
		} else {
	        MobileDevice.start();        
			Background.start();
			
	        log(MessageStyle.Important, "\nConnect a device in DFU mode");
        }
		handler = new Handler();
        addWindowListener (handler);
        setSize(500, 400);
        setVisible(true);
	}
	
	public static void main (String [] args) {
		// create Options object
		Options options = new Options();

		// add t option
		options.addOption("t", "test", false, "test all devices");
		options.addOption("f", "fetch", false, "fetch the keys from The iPhone Wiki");
		CommandLineParser parser = new PosixParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		new gui(cmd);
	}
}

class Handler extends WindowAdapter {
	public void windowClosing (WindowEvent event) {
        System.exit (0);
    }
}

