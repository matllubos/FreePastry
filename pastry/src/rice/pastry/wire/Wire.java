/*
 * Created on Jan 14, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package rice.pastry.wire;

import java.io.File;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class Wire {
  private static boolean initialized = false;
  private static int numAllowedFileDescriptors = 0;
  private static int numUsedFileDescriptors = 0;
  private static int numFDsPendingRelease = 0;
  private static float softFractionOfDescriptorsToUse = 0.5f;
  private static float notifyFractionOfDescriptorsToUse = 0.6f;
  private static ArrayList wireLimitationListeners = new ArrayList();
  private boolean unlimited = false;
  
  private static int oneOfs = 0;
  private static int oneOfsCalled = 0;
  private static int pendingsCalled = 0;
  
  static boolean outputDebug = false;
  
  /**
   * Automatic determination of ulimit -n
   */
  public static void initialize() {
    if (initialized) { return; }
    
    numAllowedFileDescriptors = 200;
    initialized = true;  
  }
  
  public static void initialize(int fileDescriptorLimit) {
    if (initialized) { return; }
    numAllowedFileDescriptors = fileDescriptorLimit;       
    initialized = true;  
  }
  
  public static void initialize(int fileDescriptorLimit, 
                                float softLimit, 
                                float notifyLimit) {
    if (initialized) { return; }

    numAllowedFileDescriptors = fileDescriptorLimit;
    softFractionOfDescriptorsToUse = softLimit;
    notifyFractionOfDescriptorsToUse = notifyLimit;  
    initialized = true;  
  }
  /**
   * Automatic determination of ulimit -n
   * @param softLimit
   * @param notifyLimit
   */
  public static void initialize(float softLimit, 
                                float notifyLimit) {  
    if (!initialized) { 
      initialize(); // automatically sets initialized = true
      softFractionOfDescriptorsToUse = softLimit;
      notifyFractionOfDescriptorsToUse = notifyLimit;
    }
  }

  private static void assertInitialized() {
    if (initialized) { return; }
    
    System.out.println("WARNING Wire was not properly initialized.");
    
    initialize();
  }
  
  public static void addWireLimitationListener(WireLimitatioinListener wll) {
    assertInitialized();
    synchronized(wireLimitationListeners) {
      wireLimitationListeners.add(wll);
    }
  }
  
  public static void removeWireLimitationListener(WireLimitatioinListener wll) {
    assertInitialized();
    synchronized(wireLimitationListeners) {
      wireLimitationListeners.remove(wll);
    }
  }
  
  private static int getSoftLimit() {
    return (int)(numAllowedFileDescriptors*softFractionOfDescriptorsToUse);
  }

  private static int getNotifyLimit() {
    return (int)(numAllowedFileDescriptors*notifyFractionOfDescriptorsToUse);
  }
  
  
  
  public static synchronized void acquireFileDescriptor() {
    if (true) return;
    assertInitialized();
    numUsedFileDescriptors++;
    oneOfs++;
    oneOfsCalled++;
    if (numUsedFileDescriptors == getNotifyLimit()) {    
      notifyWireListenersOfOverflow();      
    }
  }

  public static synchronized void releaseFileDescriptor() {
    if (true) return;
    assertInitialized();
    numUsedFileDescriptors--;
    oneOfs--;
    if (numUsedFileDescriptors == getNotifyLimit()-1) {    
      notifyWireListenersOfStatusFine();      
    }
  }
  
  
  
  public static synchronized void acquireFileDescriptors(int numFDs) {
    if (true) return;
    assertInitialized();
    numUsedFileDescriptors+=numFDs;
    int nl = getNotifyLimit();
    if ((numUsedFileDescriptors-numFDs < nl) &&
        (numUsedFileDescriptors >= nl)) {    
      notifyWireListenersOfOverflow();      
    }
  }

  public static synchronized void releaseFileDescriptors(int numFDs) {
    if (true) return;
    assertInitialized();
    numUsedFileDescriptors-=numFDs;
    int nl = getNotifyLimit();
    if ((numUsedFileDescriptors+numFDs >= nl) &&
        (numUsedFileDescriptors < nl)) {    
      notifyWireListenersOfStatusFine();          
    }
  }
  
  public static synchronized void releaseingFileDescriptor() {
    if (true) return;
    numFDsPendingRelease++;
    pendingsCalled++;
  }
  
  public static synchronized void doneReleaseingFileDescriptor() {
    if (true) return;    
    numFDsPendingRelease--;
    releaseFileDescriptor();
  }


  public static boolean needToReleaseFDs() {
    return getSoftLimit() <= (numUsedFileDescriptors+numFDsPendingRelease);
  }

  private static void notifyWireListenersOfOverflow() {
    synchronized(wireLimitationListeners) {
      if (wireLimitationListeners.size() > 0) {
        Iterator i = wireLimitationListeners.iterator();
        while (i.hasNext()) {
          WireLimitatioinListener wll = (WireLimitatioinListener)i.next();
          wll.resourcesRunningShort(numUsedFileDescriptors,numAllowedFileDescriptors);
        }
      } else {
        System.err.println("WARNING: running low on File Descriptors used:"+numUsedFileDescriptors);
      }
    }
  }

  private static void notifyWireListenersOfStatusFine() {
    synchronized(wireLimitationListeners) {
      Iterator i = wireLimitationListeners.iterator();
      while (i.hasNext()) {
        WireLimitatioinListener wll = (WireLimitatioinListener)i.next();
        wll.resourcesFine(numUsedFileDescriptors,numAllowedFileDescriptors);
      }
    }
  }
  
  /**
   * will throw an error on platforms other than Linux/BSD
   * @return
   */
  public static int getNumFDsUsed() throws IOException {
    //if (true) return 0;
    
    File dir = new File("/dev/fd").getCanonicalFile();
//            System.out.println(dir.toString());
 
    File[] files = dir.listFiles();
    
//        for (int i = 0; i < files.length; i++) {
  //                  System.out.println("  "+files[i].getCanonicalPath());
    //    }
    return files.length;
  }
  
  public static synchronized void printStatus() {
    int numFDs = -1;
    try {
      numFDs = getNumFDsUsed();
    } catch (IOException ioe) {
      ioe.printStackTrace();
    }
//    System.out.println("actual files used:"+numFDs+" acquired:"+numUsedFileDescriptors+" pending release:"+numFDsPendingRelease+" oneOfs:"+oneOfs+" pendingsCalled:"+pendingsCalled+" oneOfsCalled:"+oneOfsCalled);
    System.out.println("actual files used:"+numFDs+" acquired:"+numUsedFileDescriptors+" oneOfs:"+oneOfs+" openSocks:"+GlobalSocketManager.openSocks.size()+" oneOfsCalled:"+oneOfsCalled);
    //try { System.in.read(); } catch (IOException ioe) {}
    

  }

  private static ArrayList socketChannels = new ArrayList();
  private static ArrayList reasons = new ArrayList();

  private static boolean scThreadStarted = false;
  
  public static void registerSocketChannel(SocketChannel sc, String reason) {
    if (true) return;
    
    synchronized(socketChannels) {
      if (socketChannels.contains(sc)) {
        System.out.println("socketChannels already contains:"+sc+ "reason:"+reason);
      }
      socketChannels.add(sc);
      reasons.add(reason);

      if (!scThreadStarted) {
        scThreadStarted = true;
        new Thread(new Runnable() {
          public void run() {
            while (true) {
              try {Thread.sleep(3000); } catch (Exception e) {}
              synchronized(socketChannels) {
                System.out.println("Wire is monitoring this many channels:"+socketChannels.size());
                Iterator i = socketChannels.iterator();
                Iterator i2 = reasons.iterator();
                while (i.hasNext()) {
                  SocketChannel s = (SocketChannel)i.next();
                  String r = (String)i2.next();
                  if (s.isRegistered() || s.isOpen()) {
                    System.out.println("SC:"+s+" reason:"+r);
                  }
                }
              }            
            }
          }
        }).start();       
      }
    }
    
    
  }
  
  
 

  
}
