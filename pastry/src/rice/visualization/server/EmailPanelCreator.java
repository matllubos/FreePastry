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
import rice.email.proxy.smtp.*;

import java.util.*;

public class EmailPanelCreator implements PanelCreator {
  
  public static int NUM_DATA_POINTS = 600;
  public static int UPDATE_TIME = 60000;
  
  Vector connect = new Vector();
  Vector success = new Vector();
  Vector fail = new Vector();
  Vector times = new Vector();
  
  SmtpServer server;
  
  public EmailPanelCreator(rice.selector.Timer timer, SmtpServer server) {
    this.server = server;
    
    timer.scheduleAtFixedRate(new rice.selector.TimerTask() {
      public void run() {
        updateData();
      }
    }, 0, UPDATE_TIME);
  }
  
  public DataPanel createPanel(Object[] objects) {
    DataPanel pastPanel = new DataPanel("Email");
    
    try {      
      Constraints dataStorageCons = new Constraints();
      dataStorageCons.gridx = 1;
      dataStorageCons.gridy = 0;
      dataStorageCons.fill = Constraints.HORIZONTAL;
      
      LineGraphView dataStorageView = new LineGraphView("SMTP Server", 380, 200, dataStorageCons, "Time (m)", "Number", false, false);
      dataStorageView.addSeries("Connections", getTimeArray(), getArray(connect), Color.blue);
      dataStorageView.addSeries("Successful", getTimeArray(), getArray(success), Color.green);
      dataStorageView.addSeries("Failed", getTimeArray(), getArray(fail), Color.red);
      
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
      connect.add(new Double((double) server.getConnections()));
      success.add(new Double((double) server.getSuccess()));
      fail.add(new Double((double) server.getFail()));
      times.add(new Long(System.currentTimeMillis()));
            
      if (connect.size() > NUM_DATA_POINTS) {
        connect.removeElementAt(0); 
        times.removeElementAt(0);
        success.removeElementAt(0);
        fail.removeElementAt(0);
      }
    } catch (Exception e) {
      System.out.println("Exception " + e + " thrown.");
      e.printStackTrace();
    }
  }
}
