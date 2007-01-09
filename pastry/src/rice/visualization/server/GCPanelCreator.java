/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
package rice.visualization.server;

import rice.visualization.data.*;
import rice.environment.logging.Logger;
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
  
  /**
   * Lazilly constructed.
   */
  protected Logger logger;
  
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
      if (logger == null) past.getEnvironment().getLogManager().getLogger(EmailPanelCreator.class, null); 
      if (logger.level <= Logger.SEVERE) logger.logException(
          "Exceptoin " + e + " thrown.", e);
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
      if (logger == null) past.getEnvironment().getLogManager().getLogger(EmailPanelCreator.class, null); 
      if (logger.level <= Logger.SEVERE) logger.logException(
          "Exceptoin " + e + " thrown.",e);
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
      if (logger == null) past.getEnvironment().getLogManager().getLogger(EmailPanelCreator.class, null); 
      if (logger.level <= Logger.SEVERE) logger.logException(
          "Exceptoin " + e + " thrown.",e);
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
        if (logger == null) logger = past.getEnvironment().getLogManager().getLogger(GCPanelCreator.class, null);
        if (logger.level <= Logger.SEVERE) logger.logException(
            "Ecception " + e + " thrown.",e);
      }
    }
  }
}
