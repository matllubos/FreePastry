/*
 * Created on Jul 27, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.selector;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class ProfileSelector extends SelectorManager {
  public static boolean useHeartbeat = true;
  int HEART_BEAT_INTERVAL = 60000;
  long lastHeartBeat = 0;

  public static boolean recordStats = true;

  public String lastTaskType = null;
  public String lastTaskClass = null;
  public String lastTaskToString = null;
  public long lastTaskHash = 0;

  

	/**
	 * 
	 */
	public ProfileSelector() {
		super(true);
    new Thread(new Runnable() {
			public void run() {
        while(true) {
          System.out.println("LastTask: type:"+lastTaskType+" class:"+lastTaskClass+" toString():"+lastTaskToString+" hash:"+lastTaskHash);
          try {
            Thread.sleep(60000);
          } catch (InterruptedException ie) {
          }
          }
			}
		}, "ProfileSelectorWatchdog").start();

	}

  protected void onLoop() {
    if (!useHeartbeat) return;  
    long curTime = System.currentTimeMillis();
    if ((curTime - lastHeartBeat) > HEART_BEAT_INTERVAL) {
      System.out.println("selector heartbeat "+new Date()+" maxInvokes:"+maxInvokes);
      printStats();
      lastHeartBeat = curTime;          
    }
  }

  int maxInvokes = 0;
  public void invoke(Runnable d) {
    super.invoke(d);
    int numInvokes = invocations.size();
    if (numInvokes > maxInvokes) {
      maxInvokes = numInvokes;
    }
  }

  // *********************** debugging statistics ****************
  /**
   * Records how long it takes to receive each type of message.
   */
  public Hashtable stats = new Hashtable();
  
  public void addStat(String s, long time) {
    if (!recordStats) return;
    Stat st = (Stat)stats.get(s);
    if (st == null) {
      st = new Stat(s);
      stats.put(s,st);
    }
    st.addTime(time);
  }

  public void printStats() {
    if (!recordStats) return;
    if (stats == null) return;
    synchronized(stats) {
      Enumeration e = stats.elements();
      while(e.hasMoreElements()) {
        Stat s = (Stat)e.nextElement(); 
        System.out.println("  "+s);
      }
    }
  }

  protected void doSelections() throws IOException {
    SelectionKey[] keys = selectedKeys();

    for (int i = 0; i < keys.length; i++) {
      selector.selectedKeys().remove(keys[i]);

      SelectionKeyHandler skh = (SelectionKeyHandler) keys[i].attachment();

      if (skh != null) {
        // accept
        if (keys[i].isValid() && keys[i].isAcceptable()) {
          lastTaskType = "Accept";
          lastTaskClass = skh.getClass().getName();
          lastTaskToString = skh.toString();
          lastTaskHash = System.identityHashCode(skh);
          long startTime = System.currentTimeMillis();
          skh.accept(keys[i]);
          int time = (int)(System.currentTimeMillis() - startTime);
          lastTaskType = "Accept Complete";
          addStat("accepting",time);   
        }

        // connect
        if (keys[i].isValid() && keys[i].isConnectable()) {
          lastTaskType = "Connect";
          lastTaskClass = skh.getClass().getName();
          lastTaskToString = skh.toString();
          lastTaskHash = System.identityHashCode(skh);
          long startTime = System.currentTimeMillis();
          skh.connect(keys[i]);
          int time = (int)(System.currentTimeMillis() - startTime);
          lastTaskType = "Connect Complete";
          addStat("connecting",time);   
        }

        // read
        if (keys[i].isValid() && keys[i].isReadable()) {
          lastTaskType = "Read";
          lastTaskClass = skh.getClass().getName();
          lastTaskToString = skh.toString();
          lastTaskHash = System.identityHashCode(skh);
          long startTime = System.currentTimeMillis();
          skh.read(keys[i]);
          int time = (int)(System.currentTimeMillis() - startTime);
          lastTaskType = "Read Complete";
          addStat("reading",time);   
        }

        // write
        if (keys[i].isValid() && keys[i].isWritable()) {
          lastTaskType = "Write";
          lastTaskClass = skh.getClass().getName();
          lastTaskToString = skh.toString();
          lastTaskHash = System.identityHashCode(skh);
          long startTime = System.currentTimeMillis();
          skh.write(keys[i]);
          int time = (int)(System.currentTimeMillis() - startTime);
          lastTaskType = "Write Complete";
          addStat("writing",time);   
        }
      } else {
        keys[i].channel().close();
        keys[i].cancel();
      }
    }
  }


  /**
   * Method which invokes all pending invocations. This method should *only* be
   * called by the selector thread.
   */
  protected void doInvocations() {
    Runnable run = getInvocation();

    while (run != null) {
      try {
        lastTaskType = "Invocation";
        lastTaskClass = run.getClass().getName();
        lastTaskToString = run.toString();
        lastTaskHash = System.identityHashCode(run);
        long startTime = System.currentTimeMillis();
        run.run();
        int time = (int)(System.currentTimeMillis() - startTime);
        addStat(run.getClass().getName(),time);        
        lastTaskType = "Invocation Complete";
      } catch (Exception e) {
        System.err.println("Invoking runnable caused exception " + e + " - continuing");
        e.printStackTrace();
      }
      
      run = getInvocation();
    }

    SelectionKey key = getModifyKey();
    while (key != null) {
      if (key.isValid() && (key.attachment() != null)) {
        SelectionKeyHandler skh = (SelectionKeyHandler) key.attachment();
        lastTaskType = "ModifyKey";
        lastTaskClass = skh.getClass().getName();
        lastTaskHash = System.identityHashCode(skh);
        lastTaskToString = skh.toString();        
        skh.modifyKey(key);
        lastTaskType = "ModifyKey Complete";
      }

      key = getModifyKey();
    }
  }

  /**
   * A statistic as to how long user code is taking to process a paritcular message.
   * 
   * @author Jeff Hoye
   */
  class Stat {
    int num = 0;
    String name = null;
    long totalTime = 0;
    long maxTime = 0;
    
    public Stat(String name) {
      this.name = name;
    }
    
    public void addTime(long t) {
      num++;
      totalTime+=t;
      if (t > maxTime) {
        maxTime = t;  
      }
    }
    
    public String toString() {
      long avgTime = totalTime/num;
      return name+"\t numInstances:"+num+"\t totalTime:"+totalTime+"\t maxTime:"+maxTime+"\t avgTime:"+avgTime;
    }
  }




}
