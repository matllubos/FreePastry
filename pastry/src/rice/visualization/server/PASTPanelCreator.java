package rice.visualization.server;

import rice.visualization.data.*;
import rice.pastry.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.persistence.*;
import rice.Continuation.*;
import rice.selector.*;

import java.awt.*;
import java.util.*;

public class PASTPanelCreator implements PanelCreator {
  
  public static int NUM_DATA_POINTS = 600;
  public static int UPDATE_TIME = 1000;
  
  Vector data = new Vector();

  Vector cache = new Vector();
  
  Vector times = new Vector();
  
  protected StorageManager manager;
  protected Past past;
  
  public PASTPanelCreator(rice.selector.Timer timer, Past past) {
    this.past = past;
    
    timer.scheduleAtFixedRate(new rice.selector.TimerTask() {
      public void run() {
        updateData();
      }
    }, UPDATE_TIME, UPDATE_TIME);
  }
  
  public DataPanel createPanel(Object[] objects) {
    PastryNode node = null;
    
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
  
  protected DataPanel createPanel(PastryNode node, Past past, StorageManager manager) {
    DataPanel pastPanel = new DataPanel("PAST");
    
    GridBagConstraints pastCons = new GridBagConstraints();
    pastCons.gridx = 0;
    pastCons.gridy = 0;
    pastCons.fill = GridBagConstraints.HORIZONTAL;
    
    KeyValueListView pastView = new KeyValueListView("Overview", 380, 200, pastCons);
    
    if (past instanceof PastImpl) {
      PastImpl pastI = (PastImpl) past;
      rice.p2p.commonapi.IdRange prim = pastI.getEndpoint().range(pastI.getEndpoint().getLocalNodeHandle(), 0, null, true);
      rice.p2p.commonapi.IdRange total = pastI.getEndpoint().range(pastI.getEndpoint().getLocalNodeHandle(), pastI.getReplicationFactor(), null, true);
      pastView.add("Prim. Range", (prim == null ? "All" : prim.getCCWId() + " -> " + prim.getCWId()));
      pastView.add("Total Range", (total == null ? "All" : total.getCCWId() + " -> " + total.getCWId()));
      pastView.add("# Prim. Keys", pastI.scan(prim).numElements() + "");
      pastView.add("# Total Keys", pastI.scan(total).numElements() + "");
    } else {
      GCPastImpl pastI = (GCPastImpl) past;
      rice.p2p.commonapi.IdRange prim = pastI.getEndpoint().range(pastI.getEndpoint().getLocalNodeHandle(), 0, null, true);
      rice.p2p.commonapi.IdRange total = pastI.getEndpoint().range(pastI.getEndpoint().getLocalNodeHandle(), pastI.getReplicationFactor(), null, true);
      pastView.add("Prim. Range", (prim == null ? "All" : prim.getCCWId() + " -> " + prim.getCWId()));
      pastView.add("Total Range", (total == null ? "All" : total.getCCWId() + " -> " + total.getCWId()));
      pastView.add("# Prim. Keys", pastI.scan(prim).numElements() + "");
      pastView.add("# Total Keys", pastI.scan(total).numElements() + "");
    }
    
    pastPanel.addDataView(pastView);
    
    try {      
      GridBagConstraints dataStorageCons = new GridBagConstraints();
      dataStorageCons.gridx = 1;
      dataStorageCons.gridy = 0;
      dataStorageCons.fill = GridBagConstraints.HORIZONTAL;
      
      LineGraphView dataStorageView = new LineGraphView("Storage Size", 380, 200, dataStorageCons, "Time (sec)", "Data (B)", true);
      dataStorageView.addSeries("Data Stored", getTimeArray(), getDataArray(), Color.blue);
      
      pastPanel.addDataView(dataStorageView);
    } catch (Exception e) {
      System.out.println("Exceptoin " + e + " thrown.");
      e.printStackTrace();
    }
    try {      
      GridBagConstraints cacheCons = new GridBagConstraints();
      cacheCons.gridx = 2;
      cacheCons.gridy = 0;
      cacheCons.fill = GridBagConstraints.HORIZONTAL;
      
      LineGraphView cacheView = new LineGraphView("Cache Size", 380, 200, cacheCons, "Time (sec)", "Data (B)", true);
      cacheView.addSeries("Cache Size", getTimeArray(), getCacheArray(), Color.orange);
      
      pastPanel.addDataView(cacheView);
    } catch (Exception e) {
      System.out.println("Exceptoin " + e + " thrown.");
      e.printStackTrace();
    }
        
    return pastPanel;
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
  
  protected synchronized double[] getDataArray() {
    if (times.size() > 0) {
      double[] dataA = new double[data.size()];
      
      for (int i=0; i<dataA.length; i++) 
        dataA[i] = ((Double) data.elementAt(i)).doubleValue();
      
      return dataA;
    } else {
      return new double[0];
    }
  }
  
  protected synchronized double[] getCacheArray() {
    if (times.size() > 0) {
      double[] dataA = new double[cache.size()];
      
      for (int i=0; i<dataA.length; i++) 
        dataA[i] = ((Double) cache.elementAt(i)).doubleValue();
      
      return dataA;
    } else {
      return new double[0];
    }
  }
  
  protected synchronized void updateData() {
    if (manager != null) {
      try {
      data.add(new Double((double) getTotalSize(manager.getStorage())));
      cache.add(new Double((double) getTotalSize(manager.getCache())));
      times.add(new Long(System.currentTimeMillis()));
      
      if (data.size() > NUM_DATA_POINTS) {
        data.removeElementAt(0); 
        times.removeElementAt(0);
        cache.removeElementAt(0);
      }
      } catch (Exception e) {
        System.out.println("Ecception " + e + " thrown.");
      }
    }
  }
  
  protected int getTotalSize(Catalog catalog) throws Exception {
    ExternalContinuation c = new ExternalContinuation();
    
    catalog.getTotalSize(c);
    c.sleep();
  
    if (c.getException() == null)
      return (int) ((Long) c.getResult()).longValue();
    else
      throw c.getException();
  }
}
