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

import java.util.Queue;
import java.util.Vector;
import java.util.concurrent.PriorityBlockingQueue;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.processing.simple.*;
import rice.visualization.data.*;

public class QueuePanelCreator implements PanelCreator {
  
  public static int NUM_DATA_POINTS = 600;
  public static int UPDATE_TIME = 1000;
  
  Vector processing = new Vector();
  Vector persistence = new Vector();
  Vector invocations = new Vector();
  Vector times = new Vector();
  
  protected Queue<ProcessingRequest> processingQ;
  protected WorkQueue persistenceQ;
  
  protected Environment environment;
  
  /**
   * Lazily constructed.
   */
  protected Logger logger;  
  
  public QueuePanelCreator(Environment env, Queue<ProcessingRequest> processingQ, WorkQueue persistenceQ) {
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
      if (logger == null) logger = environment.getLogManager().getLogger(QueuePanelCreator.class, null);
      if (logger.level <= Logger.SEVERE) logger.logException(
          "",e);
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
      if (logger == null) logger = environment.getLogManager().getLogger(QueuePanelCreator.class, null);
      if (logger.level <= Logger.SEVERE) logger.logException(
          "",e);
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
      if (logger == null) logger = environment.getLogManager().getLogger(QueuePanelCreator.class, null);
      if (logger.level <= Logger.SEVERE) logger.logException(
          "",e);
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
      processing.add(new Double((double) processingQ.size()));
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
      if (logger == null) logger = environment.getLogManager().getLogger(QueuePanelCreator.class, null);
      if (logger.level <= Logger.SEVERE) logger.logException(
          "",e);
    }
  }
}
