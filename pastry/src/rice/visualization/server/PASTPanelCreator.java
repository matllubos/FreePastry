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
  Vector inserts = new Vector();
  Vector lookups = new Vector();
  Vector fetchHandles = new Vector();
  Vector others = new Vector();
  
  protected PastImpl past;
  protected String name;
  
  public PASTPanelCreator(rice.selector.Timer timer, String name, PastImpl past) {
    this.past = past;
    this.name = name;
    
    timer.scheduleAtFixedRate(new rice.selector.TimerTask() {
      public void run() {
        updateData();
      }
    }, UPDATE_TIME, UPDATE_TIME);
  }
  
  public DataPanel createPanel(Object[] objects) {
    DataPanel pastPanel = new DataPanel(name + " PAST");
    
    GridBagConstraints pastCons = new GridBagConstraints();
    pastCons.gridx = 0;
    pastCons.gridy = 0;
    pastCons.fill = GridBagConstraints.HORIZONTAL;
    
    KeyValueListView pastView = new KeyValueListView(name + " Overview", 380, 200, pastCons);
    
    rice.p2p.commonapi.IdRange prim = past.getEndpoint().range(past.getEndpoint().getLocalNodeHandle(), 0, null, true);
    rice.p2p.commonapi.IdRange total = past.getEndpoint().range(past.getEndpoint().getLocalNodeHandle(), past.getReplicationFactor(), null, true);
    pastView.add("Prim. Range", (prim == null ? "All" : prim.getCCWId() + " -> " + prim.getCWId()));
    pastView.add("Total Range", (total == null ? "All" : total.getCCWId() + " -> " + total.getCWId()));
    pastView.add("# Prim. Keys", past.scan(prim).numElements() + "");
    pastView.add("# Total Keys", past.scan(total).numElements() + "");
    pastView.add("SM", past.getStorageManager().getClass().getName());
    pastView.add("Storage", past.getStorageManager().getStorage().getClass().getName());
    pastView.add("Cache", past.getStorageManager().getCache().getClass().getName());
    pastView.add("Instance", ((PersistentStorage) past.getStorageManager().getStorage()).getName());
    pastView.add("Size", "" +  past.getStorageManager().getStorage().getTotalSize());
    
    pastPanel.addDataView(pastView);
    
    try {      
      GridBagConstraints dataStorageCons = new GridBagConstraints();
      dataStorageCons.gridx = 1;
      dataStorageCons.gridy = 0;
      dataStorageCons.fill = GridBagConstraints.HORIZONTAL;
      
      LineGraphView dataStorageView = new LineGraphView(name + " Storage Size", 380, 200, dataStorageCons, "Time (sec)", "Data (KB)", true, false);
      dataStorageView.addSeries("Data Stored", getTimeArray(), getArray(data), Color.blue);
      dataStorageView.addSeries("Cache Size", getTimeArray(), getArray(cache), Color.orange);
            
      pastPanel.addDataView(dataStorageView);
    } catch (Exception e) {
      System.out.println("Exceptoin " + e + " thrown.");
      e.printStackTrace();
    }
    
    try {      
      GridBagConstraints dataStorageCons = new GridBagConstraints();
      dataStorageCons.gridx = 2;
      dataStorageCons.gridy = 0;
      dataStorageCons.fill = GridBagConstraints.HORIZONTAL;
      
      LineGraphView dataStorageView = new LineGraphView(name + " Requests", 380, 200, dataStorageCons, "Time (sec)", "Count", false, true);
      dataStorageView.addSeries("Insert", getTimeArray(), getArray(inserts), Color.blue);
      dataStorageView.addSeries("Lookup", getTimeArray(), getArray(lookups), Color.red);
      dataStorageView.addSeries("Fetch Handle", getTimeArray(), getArray(fetchHandles), Color.green);
      dataStorageView.addSeries("Refresh", getTimeArray(), getArray(others), Color.black);
      
      pastPanel.addDataView(dataStorageView);
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
  
  protected synchronized double[] getArray(Vector vector) {
    if (vector.size() > 0) {
      double[] dataA = new double[vector.size()];
      
      for (int i=0; i<dataA.length; i++) 
        dataA[i] = ((Double) vector.elementAt(i)).doubleValue();
      
      return dataA;
    } else {
      return new double[0];
    }
  }
  
  protected synchronized void updateData() {
    try {
      data.add(new Double((double) past.getStorageManager().getStorage().getTotalSize()/1000));
      cache.add(new Double((double) past.getStorageManager().getCache().getTotalSize()/1000));
      times.add(new Long(System.currentTimeMillis()));
      inserts.add(new Double((double) past.inserts));
      lookups.add(new Double((double) past.lookups));
      fetchHandles.add(new Double((double) past.fetchHandles));
      others.add(new Double((double) past.other));
      
      past.inserts = 0;
      past.lookups = 0;
      past.fetchHandles = 0;
      past.other = 0;
      
      if (data.size() > NUM_DATA_POINTS) {
        data.removeElementAt(0); 
        times.removeElementAt(0);
        cache.removeElementAt(0);
        inserts.removeElementAt(0);
        lookups.removeElementAt(0);
        fetchHandles.removeElementAt(0);
        others.removeElementAt(0);
      }
    } catch (Exception e) {
      System.out.println("Ecception " + e + " thrown.");
      e.printStackTrace();
    }
  }
}
