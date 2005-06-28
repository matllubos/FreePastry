package rice.visualization.server;

import rice.visualization.data.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.pastry.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.persistence.*;
import rice.persistence.PersistentStorage.*;
import rice.Continuation.*;
import rice.selector.*;
import rice.pastry.dist.DistPastryNode.*;

import java.util.*;

public class QueuePanelCreator implements PanelCreator {
  
  public static int NUM_DATA_POINTS = 600;
  public static int UPDATE_TIME = 1000;
  
  Vector processing = new Vector();
  Vector persistence = new Vector();
  Vector invocations = new Vector();
  Vector times = new Vector();
  
  protected ProcessingQueue processingQ;
  protected WorkQueue persistenceQ;
  
  protected Environment environment;
  
  public QueuePanelCreator(Environment env, ProcessingQueue processingQ, WorkQueue persistenceQ) {
    this.processingQ = processingQ;
    this.persistenceQ = persistenceQ;
    this.environment = env;
    
    environment.getSelectorManager().getTimer().scheduleAtFixedRate(new rice.selector.TimerTask() {
      public void run() {
        updateData();
      }
    }, 0, UPDATE_TIME);
  }
  
  public DataPanel createPanel(Object[] objects) {
    DataPanel pastPanel = new DataPanel("Queue");

    try {      
      Constraints dataStorageCons = new Constraints();
      dataStorageCons.gridx = 1;
      dataStorageCons.gridy = 0;
      dataStorageCons.fill = Constraints.HORIZONTAL;
      
      LineGraphView dataStorageView = new LineGraphView("Processing Queue", 380, 200, dataStorageCons, "Time (s)", "Queue Size", false, false);
      dataStorageView.addSeries("Data Stored", getTimeArray(), getArray(processing), Color.blue);
      
      pastPanel.addDataView(dataStorageView);
    } catch (Exception e) {
      environment.getLogManager().getLogger(EmailPanelCreator.class, null).logException(Logger.SEVERE,
          "Exceptoin " + e + " thrown.",e);
    }
    
    try {      
      Constraints dataStorageCons = new Constraints();
      dataStorageCons.gridx = 2;
      dataStorageCons.gridy = 0;
      dataStorageCons.fill = Constraints.HORIZONTAL;
      
      LineGraphView dataStorageView = new LineGraphView("Persistence Queue", 380, 200, dataStorageCons, "Time (s)", "Queue Size", false, false);
      dataStorageView.addSeries("Insert", getTimeArray(), getArray(persistence), Color.green);
      
      pastPanel.addDataView(dataStorageView);
    } catch (Exception e) {
      environment.getLogManager().getLogger(EmailPanelCreator.class, null).logException(Logger.SEVERE,
          "Exceptoin " + e + " thrown.",e);
    }
    
    try {      
      Constraints dataStorageCons = new Constraints();
      dataStorageCons.gridx = 0;
      dataStorageCons.gridy = 0;
      dataStorageCons.fill = Constraints.HORIZONTAL;
      
      LineGraphView dataStorageView = new LineGraphView("Selector Queue", 380, 200, dataStorageCons, "Time (s)", "Queue Size", false, false);
      dataStorageView.addSeries("Insert", getTimeArray(), getArray(invocations), Color.orange);
      
      pastPanel.addDataView(dataStorageView);
    } catch (Exception e) {
      environment.getLogManager().getLogger(EmailPanelCreator.class, null).logException(Logger.SEVERE,
          "Exceptoin " + e + " thrown.",e);
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
      processing.add(new Double((double) processingQ.getLength()));
      persistence.add(new Double((double) persistenceQ.getLength()));
      invocations.add(new Double((double) environment.getSelectorManager().getNumInvocations()));
      times.add(new Long(environment.getTimeSource().currentTimeMillis()));
      
      if (processing.size() > NUM_DATA_POINTS) {
        processing.removeElementAt(0); 
        times.removeElementAt(0);
        invocations.removeElementAt(0);
        persistence.removeElementAt(0);
      }
    } catch (Exception e) {
      environment.getLogManager().getLogger(EmailPanelCreator.class, null).logException(Logger.SEVERE,
          "Ecception " + e + " thrown.",e);
    }
  }
}
