package rice.visualization.server;

import rice.visualization.data.*;
import rice.pastry.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.persistence.*;
import rice.persistence.PersistentStorage.*;
import rice.Continuation.*;
import rice.selector.*;
import rice.pastry.dist.DistPastryNode.*;

import java.awt.*;
import java.util.*;

public class QueuePanelCreator implements PanelCreator {
  
  public static int NUM_DATA_POINTS = 600;
  public static int UPDATE_TIME = 1000;
  
  Vector processing = new Vector();
  Vector persistence = new Vector();
  Vector times = new Vector();
  
  protected ProcessingQueue processingQ;
  protected WorkQueue persistenceQ;
  
  public QueuePanelCreator(rice.selector.Timer timer, ProcessingQueue processingQ, WorkQueue persistenceQ) {
    this.processingQ = processingQ;
    this.persistenceQ = persistenceQ;
    
    timer.scheduleAtFixedRate(new rice.selector.TimerTask() {
      public void run() {
        updateData();
      }
    }, UPDATE_TIME, UPDATE_TIME);
  }
  
  public DataPanel createPanel(Object[] objects) {
    DataPanel pastPanel = new DataPanel("Queue");

    try {      
      GridBagConstraints dataStorageCons = new GridBagConstraints();
      dataStorageCons.gridx = 1;
      dataStorageCons.gridy = 0;
      dataStorageCons.fill = GridBagConstraints.HORIZONTAL;
      
      LineGraphView dataStorageView = new LineGraphView("Processing Queue", 380, 200, dataStorageCons, "Time (sec)", "Queue Size", false, false);
      dataStorageView.addSeries("Data Stored", getTimeArray(), getArray(processing), Color.blue);
      
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
      
      LineGraphView dataStorageView = new LineGraphView("Persistence Queue", 380, 200, dataStorageCons, "Time (sec)", "Queue Size", false, false);
      dataStorageView.addSeries("Insert", getTimeArray(), getArray(persistence), Color.blue);
      
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
      processing.add(new Double((double) processingQ.getLength()));
      persistence.add(new Double((double) persistenceQ.getLength()));
      times.add(new Long(System.currentTimeMillis()));
      
      if (processing.size() > NUM_DATA_POINTS) {
        processing.removeElementAt(0); 
        times.removeElementAt(0);
        persistence.removeElementAt(0);
      }
    } catch (Exception e) {
      System.out.println("Ecception " + e + " thrown.");
      e.printStackTrace();
    }
  }
}
