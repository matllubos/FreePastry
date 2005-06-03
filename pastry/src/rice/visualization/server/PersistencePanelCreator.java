package rice.visualization.server;

import rice.visualization.data.*;
import rice.environment.Environment;
import rice.pastry.*;
import rice.persistence.*;
import rice.Continuation.*;
import rice.selector.*;

import java.util.*;

public class PersistencePanelCreator implements PanelCreator {
  
  public static int NUM_DATA_POINTS = 600;
  public static int UPDATE_TIME = 60000;
  
  Vector data = new Vector();
  Vector cache = new Vector();
  Vector keys = new Vector();
  Vector times = new Vector();
  
  protected StorageManagerImpl storage;
  protected String name;
  
  Environment environment;
  
  public PersistencePanelCreator(Environment env, String name, StorageManagerImpl storage) {
    this.environment = env;
    this.storage = storage;
    this.name = name;
    
    environment.getSelectorManager().getTimer().scheduleAtFixedRate(new rice.selector.TimerTask() {
      public void run() {
        updateData();
      }
    }, 0, UPDATE_TIME);
  }
  
  public DataPanel createPanel(Object[] objects) {
    DataPanel pastPanel = new DataPanel(name + " Storage");
    
    try {      
      Constraints dataStorageCons = new Constraints();
      dataStorageCons.gridx = 0;
      dataStorageCons.gridy = 0;
      dataStorageCons.fill = Constraints.HORIZONTAL;
      
      LineGraphView dataStorageView = new LineGraphView(name + " Keys", 380, 200, dataStorageCons, "Time (m)", "Number of Keys", false, false);
      dataStorageView.addSeries("Keys", getTimeArray(), getArray(keys), Color.green);
      
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
      
      LineGraphView dataStorageView = new LineGraphView(name + " Storage Size", 380, 200, dataStorageCons, "Time (m)", "Data (MB)", true, false);
      dataStorageView.addSeries("Data Stored", getTimeArray(), getArray(data), Color.blue);
      
      pastPanel.addDataView(dataStorageView);
    } catch (Exception e) {
      System.out.println("Exceptoin " + e + " thrown.");
      e.printStackTrace();
    }
    
    try {      
      Constraints dataStorageCons = new Constraints();
      dataStorageCons.gridx = 2;
      dataStorageCons.gridy = 0;
      dataStorageCons.fill = Constraints.HORIZONTAL;
      
      LineGraphView dataStorageView = new LineGraphView(name + " Cache Size", 380, 200, dataStorageCons, "Time (m)", "Data (MB)", true, false);
      dataStorageView.addSeries("Insert", getTimeArray(), getArray(cache), Color.orange);
      
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
        timesA[i] = (double) ((((Long) times.elementAt(i)).longValue() - offset) / UPDATE_TIME);
      
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
      data.add(new Double((double) storage.getStorage().getTotalSize()/(1024*1024)));
      cache.add(new Double((double) storage.getCache().getTotalSize()/(1024*1024)));
      keys.add(new Double((double) storage.scan().numElements()));
      times.add(new Long(environment.getTimeSource().currentTimeMillis()));
            
      if (data.size() > NUM_DATA_POINTS) {
        data.removeElementAt(0); 
        times.removeElementAt(0);
        cache.removeElementAt(0);
        keys.removeElementAt(0);
      }
    } catch (Exception e) {
      System.out.println("Ecception " + e + " thrown.");
      e.printStackTrace();
    }
  }
}
