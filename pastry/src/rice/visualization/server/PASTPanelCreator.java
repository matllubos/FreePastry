package rice.visualization.server;

import rice.*;
import rice.visualization.data.*;
import rice.pastry.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.persistence.*;
import rice.Continuation.*;
import rice.selector.*;

import java.util.*;

public class PASTPanelCreator implements PanelCreator {
  
  public static int NUM_DATA_POINTS = 600;
  public static int UPDATE_TIME = 1000;
  public static int REQUEST_UPDATE_OFFSET = 60;
  
  long count = 0;
  
  Vector times = new Vector();
  Vector times2 = new Vector();
  Vector outstanding = new Vector();
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
    }, 0, UPDATE_TIME);
  }
  
  public DataPanel createPanel(Object[] objects) {
    DataPanel pastPanel = new DataPanel(name + " PAST");
    
    Constraints pastCons2 = new Constraints();
    pastCons2.gridx = 0;
    pastCons2.gridy = 0;
    pastCons2.fill = Constraints.HORIZONTAL;
    
    TableView pastView2 = new TableView(name + " Outstanding Messages", 380, 200, pastCons2);
    pastView2.setSizes(new int[] {350});

    Continuation[] outstanding = past.getOutstandingMessages();
    
    for (int i=0; i<outstanding.length; i++)     
      pastView2.addRow(new String[] {outstanding[i].toString()});
       
    pastPanel.addDataView(pastView2);
    
    try {      
      Constraints dataStorageCons = new Constraints();
      dataStorageCons.gridx = 1;
      dataStorageCons.gridy = 0;
      dataStorageCons.fill = Constraints.HORIZONTAL;
      
      LineGraphView dataStorageView = new LineGraphView(name + " Outstanding", 380, 200, dataStorageCons, "Time (m)", "Outstanding", false, false);
      dataStorageView.addSeries("Insert", getTimeArray(), getArray(this.outstanding), Color.red);
      
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
      
      LineGraphView dataStorageView = new LineGraphView(name + " Requests", 380, 200, dataStorageCons, "Time (m)", "Count", false, true);
      dataStorageView.addSeries("Insert", getTimeArray2(), getArray(inserts), Color.blue);
      dataStorageView.addSeries("Lookup", getTimeArray2(), getArray(lookups), Color.red);
      dataStorageView.addSeries("Fetch Handle", getTimeArray2(), getArray(fetchHandles), Color.green);
      dataStorageView.addSeries("Refresh", getTimeArray2(), getArray(others), Color.black);
      
      pastPanel.addDataView(dataStorageView);
    } catch (Exception e) {
      System.out.println("Exceptoin " + e + " thrown.");
      e.printStackTrace();
    }
        
    return pastPanel;
  }
  
  protected synchronized double[] getTimeArray2() {
    if (times2.size() > 0) {
      double[] timesA = new double[times2.size()];
      long offset = ((Long) times2.elementAt(0)).longValue();
      
      for (int i=0; i<timesA.length; i++) 
        timesA[i] = (double) ((((Long) times2.elementAt(i)).longValue() - offset) / UPDATE_TIME);
      
      return timesA;
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
      times.add(new Long(System.currentTimeMillis()));
      outstanding.add(new Double((double) past.getOutstandingMessages().length));

      if (count % REQUEST_UPDATE_OFFSET == 0) {
        times2.add(new Long(System.currentTimeMillis()));
        inserts.add(new Double((double) past.inserts));
        lookups.add(new Double((double) past.lookups));
        fetchHandles.add(new Double((double) past.fetchHandles));
        others.add(new Double((double) past.other));
      }
      
      count++;
      
      
      past.inserts = 0;
      past.lookups = 0;
      past.fetchHandles = 0;
      past.other = 0;
      
      if (outstanding.size() > NUM_DATA_POINTS) {
        outstanding.removeElementAt(0);
        times.removeElementAt(0);
      }
      
      if (inserts.size() > NUM_DATA_POINTS) {
        times2.removeElementAt(0);
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
