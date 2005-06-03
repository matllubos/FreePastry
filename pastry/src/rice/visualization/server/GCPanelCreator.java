package rice.visualization.server;

import rice.visualization.data.*;
import rice.pastry.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.persistence.*;
import rice.Continuation.*;
import rice.selector.*;

import java.util.*;

public class GCPanelCreator implements PanelCreator {
  
  public static int NUM_DATA_POINTS = 550;
  public static int UPDATE_TIME = 60000;
  public static int NUM_HOURS = 800;
  
  Vector collected = new Vector();
  Vector refreshed = new Vector();
  
  Vector times = new Vector();
  
  protected GCPastImpl past;
  
  double[] expirations = getExpirationArray();
  
  public GCPanelCreator(rice.selector.Timer timer, Past past) {
    this.past = (GCPastImpl) past;
    
    timer.scheduleAtFixedRate(new rice.selector.TimerTask() {
      public void run() {
        updateData();
      }
    }, 0, UPDATE_TIME);
  }
  
  public DataPanel createPanel(Object[] objects) {
    PastryNode node = null;
    StorageManager manager = null;
    
    for (int i=0; i<objects.length; i++) {
      if (objects[i] instanceof PastryNode)
        node = (PastryNode) objects[i];
      else if (objects[i] instanceof StorageManager)
        manager = (StorageManager) objects[i];
      
      if ((node != null) && (past != null) && (manager != null))
        return createPanel(node, past, manager);
    }
    
    return null;
  }
  
  protected DataPanel createPanel(PastryNode node, GCPastImpl past, StorageManager manager) {
    DataPanel pastPanel = new DataPanel("GC");
    
    try {      
      Constraints dataStorageCons = new Constraints();
      dataStorageCons.gridx = 0;
      dataStorageCons.gridy = 0;
      dataStorageCons.fill = Constraints.HORIZONTAL;
      
      LineGraphView dataStorageView = new LineGraphView("Refreshed Objects", 380, 200, dataStorageCons, "Time (m)", "Number Refreshed", false, false);
      dataStorageView.addSeries("Num Objects", getTimeArray(), getRefreshedArray(), Color.blue);
      
      pastPanel.addDataView(dataStorageView);
    } catch (Exception e) {
      System.out.println("Exceptoin " + e + " thrown.");
      e.printStackTrace();
    }
    
    try {      
      Constraints dataStorageCons = new Constraints();
      dataStorageCons.gridx = 1;
      dataStorageCons.gridy = 0;
      dataStorageCons.fill = Constraints.HORIZONTAL;
      
      LineGraphView dataStorageView = new LineGraphView("Collected Objects", 380, 200, dataStorageCons, "Time (m)", "Number Collected", false, false);
      dataStorageView.addSeries("Num OBjects", getTimeArray(), getCollectedArray(), Color.blue);
      
      pastPanel.addDataView(dataStorageView);
    } catch (Exception e) {
      System.out.println("Exceptoin " + e + " thrown.");
      e.printStackTrace();
    }
    
    try {      
      Constraints cacheCons = new Constraints();
      cacheCons.gridx = 2;
      cacheCons.gridy = 0;
      cacheCons.fill = Constraints.HORIZONTAL;
      
      LineGraphView cacheView = new LineGraphView("Expiration Times", 380, 200, cacheCons, "Time (h)", "Objects", false, false);
      cacheView.addSeries("Expiration Times", expirations, getExpirations(), Color.red);
      
      pastPanel.addDataView(cacheView);
    } catch (Exception e) {
      System.out.println("Exceptoin " + e + " thrown.");
      e.printStackTrace();
    } 
    
    return pastPanel;
  }
  
  protected synchronized double[] getExpirationArray() {
    double[] result = new double[NUM_HOURS];
    
    for (int i=0; i<result.length; i++)
      result[i] = (double) i;
    
    return result;
  }
  
  protected synchronized double[] getExpirations() {
    if (past != null) {
      double[] result = new double[expirations.length];
      Iterator i = past.scan().getIterator();
      
      while (i.hasNext()) {
        GCId id = (GCId) i.next();
        long expiration = id.getExpiration();
        
        int bin = (int) ((expiration - past.getEnvironment().getTimeSource().currentTimeMillis()) / (1000 * 60 * 60));
        
        if ((bin >= 0) && (bin < result.length))
          result[bin]++;
      }
       
      return result;
    } else {
      return new double[0];
    }
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
  
  protected synchronized double[] getCollectedArray() {
    if (times.size() > 0) {
      double[] dataA = new double[collected.size()];
      
      for (int i=0; i<dataA.length; i++) 
        dataA[i] = ((Double) collected.elementAt(i)).doubleValue();
      
      return dataA;
    } else {
      return new double[0];
    }
  }
  
  protected synchronized double[] getRefreshedArray() {
    if (times.size() > 0) {
      double[] dataA = new double[refreshed.size()];
      
      for (int i=0; i<dataA.length; i++) 
        dataA[i] = ((Double) refreshed.elementAt(i)).doubleValue();
      
      return dataA;
    } else {
      return new double[0];
    }
  }
  
  protected synchronized void updateData() {
    if (past != null) {
      try {
        collected.add(new Double((double) past.collected));
        refreshed.add(new Double((double) past.refreshed));
        times.add(new Long(past.getEnvironment().getTimeSource().currentTimeMillis()));
        
        past.collected = 0;
        past.refreshed = 0;
        
        if (refreshed.size() > NUM_DATA_POINTS) {
          collected.removeElementAt(0); 
          refreshed.removeElementAt(0); 
          times.removeElementAt(0);
        }
      } catch (Exception e) {
        System.out.println("Ecception " + e + " thrown.");
      }
    }
  }
}
