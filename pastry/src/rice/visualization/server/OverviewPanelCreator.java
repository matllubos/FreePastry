package rice.visualization.server;

import rice.visualization.data.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.pastry.*;
import rice.pastry.dist.*;
import rice.selector.*;

import java.io.*;
import java.net.*;
import java.util.*;

public class OverviewPanelCreator implements PanelCreator {
  
  public static int NUM_DATA_POINTS = 600;
  public static int UPDATE_TIME = 60000;
  
  protected Vector times = new Vector();
  protected Vector used = new Vector();
  protected Vector total = new Vector();
  
  Environment environment;
  
  /**
   * Lazilly constructed.
   */
  protected Logger logger;  
  
  public OverviewPanelCreator(Environment env) {
    this.environment = env;
    env.getSelectorManager().getTimer().scheduleAtFixedRate(new rice.selector.TimerTask() {
      public void run() {
        updateData();
      }
    }, 0, UPDATE_TIME);
  }
  
  public DataPanel createPanel(Object[] objects) {
    for (int i=0; i<objects.length; i++) 
      if (objects[i] instanceof PastryNode)
        return createPanel((PastryNode) objects[i]);
    
    return null;
  }
  
  protected DataPanel createPanel(PastryNode node) {
    DataPanel nodePanel = new DataPanel("Overview");
    
    Constraints nodeCons = new Constraints();
    nodeCons.gridx = 0;
    nodeCons.gridy = 0;
    nodeCons.fill = Constraints.HORIZONTAL;
    
    KeyValueListView nodeView = new KeyValueListView("Node Information", 380, 200, nodeCons);
    nodeView.add("NodeId", node.getId().toStringFull());
    
    InetSocketAddress address = ((DistNodeHandle) node.getLocalHandle()).getAddress();
    
    nodeView.add("IP Address", address.getAddress().getHostAddress());
    nodeView.add("TCP/IP Port", address.getPort() + "");
    nodeView.add("Domain Name", address.getAddress().getHostName());
    nodeView.add("User Language", System.getProperty("user.language"));
    nodeView.add("JVM Version", System.getProperty("java.version"));
    nodeView.add("JVM Provider", System.getProperty("java.vendor"));
    nodeView.add("JVM Location", System.getProperty("java.home"));
    nodeView.add("Op. System", System.getProperty("os.name") + " " + System.getProperty("os.version"));
    nodeView.add("O.S. Arch.", System.getProperty("os.arch"));
    
    Constraints jvmCons = new Constraints();
    jvmCons.gridx = 1;
    jvmCons.gridy = 0;
    jvmCons.fill = Constraints.HORIZONTAL;
    
    TableView threadView = new TableView("Active Threads", 380, 200, jvmCons);
    
    ThreadGroup group = Thread.currentThread().getThreadGroup();
    Thread[] threads = new Thread[group.activeCount()];
    group.enumerate(threads);
    
    for (int i=0; i<threads.length; i++)
      if (threads[i] != null)
        threadView.addRow(new String[] {threads[i].getName(),  
                                        (threads[i].isAlive() ? "live" : "dead"), 
                                        (threads[i].isDaemon() ? "D" : ""), 
                                        "" + threads[i].getPriority()});
    
    threadView.setSizes(new int[] {290, 35, 10, 10});
    
    Constraints memoryCons = new Constraints();
    memoryCons.gridx = 2;
    memoryCons.gridy = 0;
    memoryCons.fill = Constraints.HORIZONTAL;
    
    LineGraphView memoryView = new LineGraphView("Memory Usage", 380, 200, memoryCons, "Time (m)", "Memory Used (MB)", true, false);
    memoryView.addSeries("Total Memory", getTimeArray(), getTotalMemoryArray(), Color.green);
    memoryView.addSeries("Used Memory", getTimeArray(), getUsedMemoryArray(), Color.blue);
    
    nodePanel.addDataView(memoryView);
    nodePanel.addDataView(nodeView);
    nodePanel.addDataView(threadView);
    
    return nodePanel;
  }
  
  
  
  protected synchronized double[] getTimeArray() {
    if (times.size() > 0) {
      double[] timesA = new double[times.size()];
      long offset = ((Long) times.elementAt(0)).longValue();
      
      for (int i=0; i<timesA.length; i++) 
        timesA[i] = (double) ((((Long) times.elementAt(i)).longValue() - offset) / UPDATE_TIME);
      
      return timesA;
    } else {
      return new double[0];
    }
  }
  
  protected synchronized double[] getUsedMemoryArray() {
    if (times.size() > 0) {
      double[] dataA = new double[used.size()];
      
      for (int i=0; i<dataA.length; i++) 
        dataA[i] = (double) ((Long) used.elementAt(i)).longValue() / (1024*1024);
      
      return dataA;
    } else {
      return new double[0];
    }
  }
  
  protected synchronized double[] getTotalMemoryArray() {
    if (times.size() > 0) {
      double[] dataA = new double[total.size()];
      
      for (int i=0; i<dataA.length; i++) 
        dataA[i] = (double) ((Long) total.elementAt(i)).longValue() / (1024*1024);
      
      return dataA;
    } else {
      return new double[0];
    }
  }
  
  protected synchronized void updateData() {
    try {
      total.add(new Long(Runtime.getRuntime().totalMemory()));
      used.add(new Long(Runtime.getRuntime().freeMemory()));
      times.add(new Long(environment.getTimeSource().currentTimeMillis()));
      
      if (total.size() > NUM_DATA_POINTS) {
        total.removeElementAt(0); 
        used.removeElementAt(0); 
        times.removeElementAt(0); 
      }
    } catch (Exception e) {
      if (logger == null) logger = environment.getLogManager().getLogger(OverviewPanelCreator.class, null);
      if (logger.level <= Logger.SEVERE) logger.logException(
          "Ecception " + e + " thrown.",e);
    }
  }
  
  
  
}
