/*
 * Created on May 12, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.watchdog;

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class Watchdog implements Runnable {
  
// ************************ Defaults ***************************
  public static int def_pingPort = 5009;
  public static int def_checkDelay = 60000;
  public static int def_sleepTime = 1000;
  public static int def_pingTimes = 3;
  public static int def_pingDelay = 1000;
  public static int def_port = 26523;
//  public static PingMessageFactory def_PingMessageFactory = new SocketPingMessageFactory(def_port);
  public static boolean def_usePing = true;
  public static int def_estBootTime = 60000;
  public static int def_livenessCheckFrequency = 60000;
  public static int def_maxPingFrequency = 1000;
  public static int def_sleepBuffer = 60000;
  public static boolean def_usePassword = true;
  public static int pwFileLifetime = 60000;
  public static boolean def_useStdOut = false;
  public static String def_outputFileName = "watchdog.out";
  public static char[] def_password = null;
  
  /**
   * How often we check everything.
   */
  private int checkDelay = def_checkDelay;

  /**
   * How often we wake up to print streams.
   */
  private int sleepTime = def_sleepTime;

  /**
   * Number of times to ping before calling a node dead.
   */
	private int pingTimes = def_pingTimes;
  
  /**
   * The port to ping the watched app on.
   */
	private int pingPort = def_pingPort;

  /**
   * The amount of time to wait before pinging him the first time.
   */
	private int estBootTime = def_estBootTime;
  
  /**
   * The string to execute when we restart
   */
	private String execString;
  
  /**
   * The string of the directory to execute from.
   */
  private String execDir;
  
  /**
   * The port to run our ping receiver on.
   */
  private int port = def_port;

  /**
   * How often we need to ping this guy to determine if he stalled
   */
  int livenessCheckFrequency = def_livenessCheckFrequency; 
  
  /** 
   * When we are pinging him, how often we can ping in a row.
   */
  int maxPingFrequency = def_maxPingFrequency;
  
  /**
   * This is the buffer time of how long we took to loop before 
   * declaring that the computer got put to sleep.  
   * 
   * If this is too
   * low and we have a lot of CPU conentention and don't get to loop
   * within this time + the sleepTime, we will incorrectly determine
   * that the box was put to sleep.
   * 
   * If this is too high, we cannot determine that we were asleep.
   */
  private int sleepBuffer = def_sleepBuffer;
  
  
  /**
   * Whether we want to detect application stalling by pinging the app.
   */
  boolean usePing = def_usePing;

  /**
   * The message to send to the app.  If this is null, then we'll try the 
   * PingMessageFactory pmf.
   */
  private Serializable pingMsg = null;
  
  /**
   * If this is null, and pingMsg is null then we'll not ping.
   */
  private PingMessageFactory pmf;// = def_PingMessageFactory;  

  /** 
   * Maximum amount of (re)starts per hour.
   */  
  int TIMES_PER_HOUR = 3;
  /** 
   * Maximum amount of (re)starts per day.
   */  
  int TIMES_PER_DAY = 10;

  boolean usePassword = def_usePassword;
  char[] password = def_password;
  File pwFile;
  
  boolean useStdOut = def_useStdOut;
  String outputFileName = def_outputFileName;
  
	/**
   * @param s_pingPort
   * @param s_execString
   * @param s_rate
   * @param s_pingMsg
   * @param s_pingTimes
   * @param s_pingDelay
   */
  public Watchdog(String execString, String execDir) throws Exception {
    this.execString = execString;
    this.execDir = execDir;
    pmf = new SocketPingMessageFactory(port);
    if (!useStdOut) {
      try {
        PrintStream ps =
          new PrintStream(new FileOutputStream(outputFileName));
        System.setOut(ps);
        System.setErr(ps);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    
    if (usePassword && password == null) {
      // get password
      password = getPassword();
    }
    pingAddress = new InetSocketAddress(InetAddress.getLocalHost(), pingPort);   
    if ((pmf == null) && (pingMsg == null) && (usePing)) {
      pmf = new SocketPingMessageFactory(port);
    }
    //println("PingAddress = "+pingAddress); 
    setupPinger();
    
    
  }

  boolean doneGettingPw = false;
  Object pwMonitor = new Object();
  public char[] getPassword() {
    JPanel p = new JPanel();
    p.setLayout(new GridLayout(0,2));

    JLabel label = new JLabel("Please enter your password");
    JPasswordField pwf = new JPasswordField();
    
    pwf.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
        doneGettingPw = true;
        synchronized(pwMonitor) {
          pwMonitor.notifyAll();  
        }
			}
		});
    
    p.add(label);

    doneGettingPw = false;
    final JFrame f = new JFrame("Password Input");

    JPanel p1 = new JPanel();
    p1.setLayout(new GridLayout(2,0));
    p1.add(pwf);
    p1.add(new JButton(new AbstractAction("Enter") {
			public void actionPerformed(ActionEvent arg0) {
        doneGettingPw = true;
        synchronized(pwMonitor) {
          pwMonitor.notifyAll();  
        }
			}
		}));
    p.add(p1);
    
    f.getContentPane().add(p);
    f.pack();    
    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    f.setVisible(true);
    //boolean doneGettingPw = false;

    while(!doneGettingPw) {
      synchronized(pwMonitor) {
        try {
          pwMonitor.wait();
        } catch (InterruptedException ie) {
          ie.printStackTrace();
          doneGettingPw = true;
        }
      }
    }
    char[] pw = pwf.getPassword();
    f.setVisible(false);
    
    return pw;
  }
  
  
  /**
   * The address of the application that we are going to ping
   */
  InetSocketAddress pingAddress;
  private Process proc = null;
  private long lastTime = 0;
  InetAddress lastAddress = null;
  private boolean running = false;
  BufferedReader stdO;
  BufferedReader stdE;  

  
  public void start() throws IOException {
    try {
      lastAddress = InetAddress.getLocalHost();      
    } catch (UnknownHostException uhe) {
      uhe.printStackTrace();
    }
    running = true;
    launchProc("Launching proc for first time.");
    lastTime = System.currentTimeMillis();
    new Thread(this,"WatchdogThread").start();
  }
  
  public void stop() {
    running = false;
  }
  
  long lastCheckTime = 0;
  public void run() {
    println("Running");
    while(running) {

      try {
        if (alive) {
          printError();
          printOut();
          pingProc(false);
        }        
      } catch (Throwable t) {
        alive = false;
      }
      
      

      long curTime = System.currentTimeMillis();
    
      managePWFile();      
      
      if (curTime-lastCheckTime > checkDelay) {
        lastCheckTime = curTime;        
        if (checkIPaddress()) {
          checkLocalBoxLiveness();
          checkProcLiveness();
          try {
            pingProc(true);
          } catch (Exception ioe) {
            ioe.printStackTrace();
          }
        } else {
          // address is not usable, don't do anything yet
          lastTime = System.currentTimeMillis();
        }
      }
      
      // sleep for a while      
      try {
        Thread.sleep(sleepTime);    
      } catch (InterruptedException ie) {
        ie.printStackTrace();
        running = false;
      }
      
    }
  }
  
  public void println(String out) {
    System.out.println("WATCHDOG["+System.currentTimeMillis()+"]:"+out);
  }
  
  public void printError() {
    if (stdE == null) return;
    try {
      while(stdE.ready()) {
        System.err.println(stdE.readLine());   
      }
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  public void printOut() {
    if (stdO == null) return;
    try {
      while(stdO.ready()) {
        System.out.println(stdO.readLine());   
      }
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  boolean alive = true;

	/**
	 * 
	 */
	private void checkProcLiveness() {
    if (proc == null) return;
    boolean checkAlive = false;
    try {
      proc.exitValue();
    } catch (IllegalThreadStateException itse) {
      checkAlive = true;
    }		
    alive = checkAlive;
//  println("procLiveness:"+alive);
    if (!alive) {
//      println("Proc DEAD, relaunching....");
      launchProcNoExcept("Proc DEAD, relaunching....");
    }
	}

	/**
	 * 
	 */
	private void checkLocalBoxLiveness() {
    long newTime = System.currentTimeMillis();
    long elapsed = newTime - lastTime;
    if (elapsed > sleepBuffer+checkDelay) {
      killProc("Killing because Computer put to sleep! Elapsed Time = "+elapsed);
      launchProcNoExcept("Restarting because Computer put to sleep! Elapsed Time = "+elapsed);
    }
    lastTime = System.currentTimeMillis();
	}

	/**
	 * 
	 */
	private boolean checkIPaddress() {
    boolean changed = false;
    boolean restart = false;
    InetAddress newAddr = null;
    try {
      newAddr = InetAddress.getLocalHost();      
      if (!newAddr.equals(lastAddress)) {
        changed = true;
      }
      lastAddress = newAddr;
    } catch (UnknownHostException uhe) {
      uhe.printStackTrace();
      changed = true;      
    }
    
    if (changed) {
      pmf.addressChanged();
      killProc("Internet Address Changed... killing");
    }
    
    if (lastAddress.isLoopbackAddress()) {
      return false;
    }
		
    if (changed) {
      return checkInternetAccess();
    }
    return true;
	}

  private boolean checkInternetAccess() {
    return true;
  }
  

  private void launchProcNoExcept(String reason) {
    try {
      launchProc(reason);
    } catch (IOException ioe){
      ioe.printStackTrace();
    }
  }

  long pwFileCreationTime = 0;
  
  private void managePWFile() {
    if ((pwFile != null) && ((System.currentTimeMillis() - pwFileCreationTime) > pwFileLifetime)) {
      pwFile.delete();
      pwFile = null; 
      println("delected PW file");
    }
  }

	private void launchProc(String reason) throws IOException {
    if (!canRestart()) return;

    println(reason);
    restartTimes.add(0,new Long(System.currentTimeMillis()));
    while (restartTimes.size() > failureHistoryLength) {
      restartTimes.remove(restartTimes.size()-1); // remove last entry
    }
    String tempExecString = execString;
    if (usePassword) {
      try {
        createPwFile();
        
        tempExecString+="-pw "+pwFile.getAbsolutePath();
      } catch (Exception e) {
        e.printStackTrace();
      }
      println("executing "+tempExecString);
    }
    if (execDir != null) {
      proc = Runtime.getRuntime().exec(tempExecString,null,new File(execDir));      
    } else {
      proc = Runtime.getRuntime().exec(tempExecString);
    }
    stdO = new BufferedReader(new InputStreamReader(proc.getInputStream()));
    stdE = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
    lastPingReceived = System.currentTimeMillis()+estBootTime;
    pinging = false;
    numPings = 0;
//    println("launchProc().finished");
  }


  private void createPwFile() throws Exception {
    pwFile = new File("jkdasf");
    PrintWriter pw = new PrintWriter(new FileOutputStream(pwFile));
    pw.println(password);
//    pw.println("moo");
    pw.flush();
    pw.close();
    pwFileCreationTime = System.currentTimeMillis();    
  }

  private void killProc(String reason) {
    if (proc != null) {
      println(reason);
      proc.destroy();
      try {
        proc.waitFor();
      } catch (Exception e) {
        e.printStackTrace();
      }
      proc = null;
      println("Done Killing Proc");
    }
  }
  
  // ************************** Manage maximum restarts **************************
  // most recent first
  ArrayList restartTimes = new ArrayList();
  int failureHistoryLength = Math.max(TIMES_PER_DAY,TIMES_PER_HOUR);
  int HOUR = 60000*15;//60;
  int DAY = HOUR*24;
  
  boolean firstHourFailure = false;
  boolean firstDayFailure = false;
  
  /**
   * 3 per minute
   * 5 per hour
   * 10 per day
   * @return
   */
  boolean canRestart() {
    long curTime = System.currentTimeMillis();    
    Long l;
    
    // Hour test
    if (restartTimes.size() < TIMES_PER_HOUR) {
      return true;      
    }
    l = (Long)restartTimes.get(TIMES_PER_HOUR-1);
    if ((curTime - l.longValue()) < HOUR) {
      if (!firstHourFailure) {
        println("Too Many Failures in an hour, not restarting");
        firstHourFailure = true;
      }
      return false;  
    }
    
    // day test
    if (restartTimes.size() < TIMES_PER_DAY) {
      firstHourFailure = false;
      return true;      
    }
    l = (Long)restartTimes.get(TIMES_PER_DAY-1);
    if ((curTime - l.longValue()) < DAY) {
      if (!firstDayFailure) {
        println("Too Many Failures in a day, not restarting");
        firstDayFailure = true;
      }
      return false;  
    }    
    
    firstHourFailure = false;
    firstDayFailure = false;
    return true;
  }


  // ********************** Ping stuff **********************
  
  public static int DATAGRAM_SEND_BUFFER_SIZE = 65536;
  public static int DATAGRAM_RECEIVE_BUFFER_SIZE = 131072;

  /**
   * the channel used from talking to the network
   */
  private DatagramChannel channel;

  /**
   * the key used to determine what has taken place
   */
  private SelectionKey key;

  private Selector selector;
  private ByteBuffer buffer;
    

  private void setupPinger() throws IOException {
    buffer = ByteBuffer.allocateDirect(DATAGRAM_SEND_BUFFER_SIZE);

    selector = Selector.open();
    channel = DatagramChannel.open();
    channel.configureBlocking(false);
    InetSocketAddress isa = new InetSocketAddress(port);
    channel.socket().bind(isa); // throws BindException
    channel.socket().setSendBufferSize(DATAGRAM_SEND_BUFFER_SIZE);
    channel.socket().setReceiveBufferSize(DATAGRAM_RECEIVE_BUFFER_SIZE);

    key = channel.register(selector, SelectionKey.OP_READ);
  }
  
  boolean pinging = false;
  long lastPingReceived = 0;
  int numPings = 0;
  long lastTimePinged = 0;
  
  /**
   * 
   */
  private void pingProc(boolean canStartPinging) throws Exception {
    if (!usePing) return; // this is not an app we can ping
    //println("PingProc!");
    receivePing();
    if (pinging) {
      if (numPings < pingTimes) {
        ping();      
      } else {
        killProc("Node appears to have stalled:  killing");
        launchProc("Node appears to have stalled:  restarting..."); 
      }      
    } else {
      if (canStartPinging) {
        long lastPingReceivedDuration = System.currentTimeMillis() - lastPingReceived;
        if (lastPingReceivedDuration > livenessCheckFrequency) {
          pinging = true;
          numPings = 0;
          ping();
        }
      }
    }
  }
  
  private void ping() throws Exception {
    long curTime = System.currentTimeMillis();
    long lastTimePingedDuration = curTime - lastTimePinged;
    if (lastTimePingedDuration < maxPingFrequency) 
      return;
    lastTimePinged = System.currentTimeMillis();
    numPings++;
    Serializable lpm = pingMsg;
    if (lpm == null) {
      if (pmf != null) {
        lpm = pmf.getPingMessage();
      }
    }
    if (lpm == null) {
      usePing = false;
      return;
    }
    ByteBuffer buf = serialize(lpm);
    //println("Ping "+pingAddress);
    int num = channel.send(buf, pingAddress);    
  }
  
  private void pingResponse(InetSocketAddress address) {
    //println("Received data from address " + address);        
    lastPingReceived = System.currentTimeMillis();     
    pinging = false;
    numPings = 0; 
  }
  
  private void receivePing() throws IOException {
    int i = selector.select(100);
    if (i > 0) {
      //println("receivePing() got something");
      InetSocketAddress address = null;
  
      while ((address = (InetSocketAddress) channel.receive(buffer)) != null) {
  
        buffer.flip();
  
        if (buffer.remaining() > 0) {
          pingResponse(address);
        } else {          
        }
        buffer.clear();
      }
      selector.selectedKeys().remove(key);                                      
    } else {
      //if (pinging) println("No Ping Response");
    }
  }

  public ByteBuffer serialize(Object o) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);

    // write out object and find its length
    oos.writeObject(o);

    int len = baos.toByteArray().length;
    //println("serializingD " + o + " len=" + len);

    return ByteBuffer.wrap(baos.toByteArray());
  }


  // ***************** Command Line Stuff *********************

	public static void main(String[] args) throws Exception {
//    new Watchdog("java rice.pastry.testing.DistHelloWorldMultiThread",
//                 "c:\\pastry\\pastry\\classes").start();
    
    doInitstuff(args);
//    if ((s_pingPort == 0) || s_execString == null) {
//      printHelpAndExit(); 
//    }
    new Watchdog(s_execString, s_dir).start();
  }
  
  private static String s_execString;
  private static String s_dir = null;

	private static void doInitstuff(String args[]) throws Exception {
    int execArg = -1;
    // process command line arguments

    // print all args
//    for (int i = 0; i < args.length; i++) {
//      System.out.println(i+":"+args[i]);
//    }


    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-help")) {
        printHelpAndExit(); 
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("--help")) {
        printExtendedHelpAndExit(); 
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-exec")) {
        s_execString = "";
        execArg = i;
        i++;
        for (;i<args.length;i++) {
          s_execString+=args[i]+" ";
        }
      }
    }

    // verify they gave us an exec arg
    if ((execArg == -1) || s_execString.equals("")) {
      printHelpAndExit();
    }

    for (int i = 0; i < execArg; i++) {
      if (args[i].equals("-dir") && i + 1 < args.length) {
        s_dir = args[i + 1];
      }
    }
    
    for (int i = 0; i < execArg; i++) {
      if (args[i].equals("-noping") && i + 1 < args.length) {
        def_usePing = false;
      }
    }

    for (int i = 0; i < execArg; i++) {
      if (args[i].equals("-pw") && i + 1 < args.length) {
        def_usePassword = true;
      }
    }

    for (int i = 0; i < execArg; i++) {
      if (args[i].equals("-nopw") && i + 1 < args.length) {
        def_usePassword = false;
      }
    }

    for (int i = 0; i < execArg; i++) {
      if (args[i].equals("-pword") && i + 1 < args.length) {
        def_password = args[i+1].toCharArray();
      }
    }

    for (int i = 0; i < execArg; i++) {
      if (args[i].equals("-port") && i + 1 < args.length) {
        def_port = Integer.parseInt(args[i + 1]);
      }
    }

    for (int i = 0; i < execArg; i++) {
      if (args[i].equals("-pingport") && i + 1 < args.length) {
        def_pingPort = Integer.parseInt(args[i + 1]);
      }
    }

/*
    for (int i = 0; i < execArg; i++) {
      if (args[i].equals("-pingmsgfactory") && i + 1 < args.length) {
        Class clazz = Class.forName(args[i + 1]);
        Constructor[] ctors = clazz.getConstructors();
        def_PingMessageFactory = (PingMessageFactory)ctors[0].newInstance(new Object[0]);        
      }
    }
*/
    for (int i = 0; i < execArg; i++) {
      if (args[i].equals("-stdout") && i + 1 < args.length) {
        def_useStdOut = true;
      }
    }

    for (int i = 0; i < execArg; i++) {
      if (args[i].equals("-outfile") && i + 1 < args.length) {
        def_outputFileName = args[i + 1];
      }
    }
	}
  public static void printExtendedHelpAndExit() {
    printBasicHelp();
    System.out.println("  -exec: This must be the last argument to watchdog, everything after it goes to command line of the program to be executed.");
    System.out.println("  [-dir d]: specify the working directory default=current directory");
    System.out.println("  [-pw]: prompt user for password, and pass it to file default usePass="+def_usePassword);
    System.out.println("  [-nopw]: do not prompt user for password, and pass it to file default usePass="+def_usePassword);
    System.out.println("  [-pword p]: use this password");
    System.out.println("  [-noping]: don't use pings to determine liveness. default use ping="+def_usePing);
    System.out.println("  [-port p]: specify the port that the watchdog will listen to pings. default="+def_port);
    System.out.println("  [-pingport p]: specify the port that your app will respond to pings. default="+def_pingPort);
//    System.out.println("  [-pingmsgfactory f]: specify the class name of a class implementing PingManagerFactory who's only constructor takes no arguments.  default="+def_PingMessageFactory.getClass().getName());
    System.out.println("  [-stdout]: use standard out/standarderr default="+def_useStdOut);
    System.out.println("  [-outfile f]: send output to this file name default="+def_outputFileName);
    System.exit(1);
  }

  public static void printBasicHelp() {
    System.out.println("Usage: Watchdog [-dir workingDirectroy] -exec commandline");
    System.out.println("  This will execute the commandline argument with default args");
    System.out.println("  use --help for help and a list of all parameters");
  }

  public static void printHelpAndExit() {
    printBasicHelp();
    System.exit(1);
  }

}
