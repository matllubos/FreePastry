package rice.visualization.server;

import rice.visualization.data.*;
import rice.pastry.*;
import rice.pastry.dist.*;
import rice.selector.*;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class OverviewPanelCreator implements PanelCreator {
  
  public static int NUM_DATA_POINTS = 600;
  public static int UPDATE_TIME = 1000;
  
  protected Vector times = new Vector();
  protected Vector used = new Vector();
  protected Vector total = new Vector();
  
  public OverviewPanelCreator(rice.selector.Timer timer) {
    timer.scheduleAtFixedRate(new rice.selector.TimerTask() {
      public void run() {
        updateData();
      }
    }, UPDATE_TIME, UPDATE_TIME);
  }
  
  public DataPanel createPanel(Object[] objects) {
    for (int i=0; i<objects.length; i++) 
      if (objects[i] instanceof PastryNode)
        return createPanel((PastryNode) objects[i]);
    
    return null;
  }
  
  protected DataPanel createPanel(PastryNode node) {
    DataPanel nodePanel = new DataPanel("Basic Information");
    
    GridBagConstraints nodeCons = new GridBagConstraints();
    nodeCons.gridx = 0;
    nodeCons.gridy = 0;
    nodeCons.fill = GridBagConstraints.HORIZONTAL;
    
    KeyValueListView nodeView = new KeyValueListView("Network Information", 380, 200, nodeCons);
    nodeView.add("NodeId", node.getId().toStringFull());
    
    InetSocketAddress address = ((DistNodeHandle) node.getLocalHandle()).getAddress();
    
    nodeView.add("IP Address", address.getAddress().getHostAddress());
    nodeView.add("TCP/IP Port", address.getPort() + "");
    nodeView.add("Domain Name", address.getAddress().getHostName());
    nodeView.add("User Language", System.getProperty("user.language"));
    
    GridBagConstraints jvmCons = new GridBagConstraints();
    jvmCons.gridx = 1;
    jvmCons.gridy = 0;
    jvmCons.fill = GridBagConstraints.HORIZONTAL;
    
    KeyValueListView jvmView = new KeyValueListView("JVM/System Information", 380, 200, jvmCons);
    jvmView.add("JVM Version", System.getProperty("java.version"));
    jvmView.add("JVM Provider", System.getProperty("java.vendor"));
    jvmView.add("JVM Location", System.getProperty("java.home"));
    jvmView.add("Op. System", System.getProperty("os.name") + " " + System.getProperty("os.version"));
    jvmView.add("O.S. Arch.", System.getProperty("os.arch"));
    
    GridBagConstraints memoryCons = new GridBagConstraints();
    memoryCons.gridx = 2;
    memoryCons.gridy = 0;
    memoryCons.fill = GridBagConstraints.HORIZONTAL;
    
    LineGraphView memoryView = new LineGraphView("Memory Usage", 380, 200, memoryCons, "Time (sec)", "Memory Used (B)", true, false);
    memoryView.addSeries("Total Memory", getTimeArray(), getTotalMemoryArray(), Color.green);
    memoryView.addSeries("Used Memory", getTimeArray(), getUsedMemoryArray(), Color.blue);
    
    nodePanel.addDataView(memoryView);
    nodePanel.addDataView(nodeView);
    nodePanel.addDataView(jvmView);
    
    return nodePanel;
  }
  
  
  
  protected synchronized double[] getTimeArray() {
    if (times.size() > 0) {
      double[] timesA = new double[times.size()];
      long offset = ((Long) times.elementAt(0)).longValue();
      
      for (int i=0; i<timesA.length; i++) 
        timesA[i] = (double) ((((Long) times.elementAt(i)).longValue() - offset) / 1000);
      
      return timesA;
    } else {
      return new double[0];
    }
  }
  
  protected synchronized double[] getUsedMemoryArray() {
    if (times.size() > 0) {
      double[] dataA = new double[used.size()];
      
      for (int i=0; i<dataA.length; i++) 
        dataA[i] = (double) ((Long) used.elementAt(i)).longValue();
      
      return dataA;
    } else {
      return new double[0];
    }
  }
  
  protected synchronized double[] getTotalMemoryArray() {
    if (times.size() > 0) {
      double[] dataA = new double[total.size()];
      
      for (int i=0; i<dataA.length; i++) 
        dataA[i] = (double) ((Long) total.elementAt(i)).longValue();
      
      return dataA;
    } else {
      return new double[0];
    }
  }
  
  protected synchronized void updateData() {
    try {
      total.add(new Long(Runtime.getRuntime().totalMemory()));
      used.add(new Long(Runtime.getRuntime().freeMemory()));
      times.add(new Long(System.currentTimeMillis()));
      
      if (total.size() > NUM_DATA_POINTS) {
        total.removeElementAt(0); 
        used.removeElementAt(0); 
        times.removeElementAt(0); 
      }
    } catch (Exception e) {
      System.out.println("Ecception " + e + " thrown.");
    }
  }
  
  
  
}
