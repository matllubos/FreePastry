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
import rice.pastry.*;
import rice.p2p.aggregation.*;
import rice.persistence.*;
import rice.selector.*;
import rice.Continuation.*;

import java.util.*;

public class AggregationPanelCreator implements PanelCreator {
  
  public static final long SECONDS = 1000;
  public static final long MINUTES = 60 * SECONDS;
  public static final long HOURS = 60 * MINUTES;
  public static final long DAYS = 24 * HOURS;
  
  public static final long UPDATE_TIME = 5 * SECONDS;
  public static final long STATS_GRANULARITY = 10 * MINUTES;
  public static final long STATS_RANGE = 2 * DAYS;
  public static final int STATS_HISTORY = (int)(STATS_RANGE / STATS_GRANULARITY);
  public static final int STATS_SUBSAMPLE = (int)(STATS_GRANULARITY / UPDATE_TIME);
  
  AggregationStatistics[] statsHistory = new AggregationStatistics[STATS_HISTORY];
  AggregationStatistics lastStats = null;
  int statsPtr = 0, statsTotal = 0, statsCounter = 0;
  AggregationImpl aggregation;
  
  public AggregationPanelCreator(rice.selector.Timer timer, AggregationImpl aggregation) {
    this.aggregation = aggregation;

    timer.scheduleAtFixedRate(new rice.selector.TimerTask() {
      public void run() {
        updateData();
      }
    }, (int)UPDATE_TIME, (int)UPDATE_TIME);
  }
  
  public DataPanel createPanel(Object[] objects) {

    DataPanel summaryPanel = new DataPanel("Aggregation");

    Constraints cons = new Constraints();
    cons.gridx = 0;
    cons.gridy = 0;
    cons.fill = Constraints.HORIZONTAL;
    
    KeyValueListView aggregationView = new KeyValueListView("Aggregation Overview", 380, 200, cons);
    aggregationView.add("Obj. waiting", ""+aggregation.getNumObjectsWaiting());
    if (lastStats != null) {
      aggregationView.add("Aggregates", lastStats.numAggregatesTotal+" ("+lastStats.numPointerArrays+"P "+lastStats.criticalAggregates+"C "+lastStats.orphanedAggregates+"O)");
      aggregationView.add("Obj. total", lastStats.numObjectsTotal+" ("+lastStats.totalObjectsSize+" bytes)");
      aggregationView.add("Obj. alive", lastStats.numObjectsAlive+" ("+lastStats.liveObjectsSize+" bytes)");

      int numRealAggregates = lastStats.numAggregatesTotal - lastStats.numPointerArrays;
      if (numRealAggregates > 0)
        aggregationView.add("Aggr. factor", ""+(((float)lastStats.numObjectsAlive) / numRealAggregates));
    }

    aggregationView.add("Obj. store", aggregation.getObjectStore().getClass().getName());
    aggregationView.add("Aggr. store", aggregation.getAggregateStore().getClass().getName());
    
    rice.p2p.commonapi.Id currentHandle = (rice.p2p.commonapi.Id) aggregation.getHandle();
    aggregationView.add("Current root", ((currentHandle == null) ? "null" : ((rice.p2p.multiring.RingId)currentHandle).getId().toStringFull()));

    Constraints graphCons = new Constraints();
    graphCons.gridx = 1;
    graphCons.gridy = 0;
    graphCons.fill = Constraints.HORIZONTAL;

    LineGraphView graphView = new LineGraphView("Lifetime", 380, 200, graphCons, "Hours from now", "Occurrences", false, false);
    if (lastStats != null) {
      double[] objSeries = new double[lastStats.objectLifetimeHisto.length];
      double[] aggrSeries = new double[lastStats.objectLifetimeHisto.length];
      double[] timeSeries = new double[lastStats.objectLifetimeHisto.length];
      for (int i=0; i<lastStats.objectLifetimeHisto.length; i++) {
        objSeries[i] = lastStats.objectLifetimeHisto[i];
        aggrSeries[i] = lastStats.aggregateLifetimeHisto[i];
        timeSeries[i] = ((double)(i*lastStats.granularity)) / HOURS;
      }
        
      graphView.addSeries("Objects", timeSeries, objSeries, Color.green);
      graphView.addSeries("Aggregates", timeSeries, aggrSeries, Color.blue);
    }

    Constraints graphCons2 = new Constraints();
    graphCons2.gridx = 2;
    graphCons2.gridy = 0;
    graphCons2.fill = Constraints.HORIZONTAL;

    LineGraphView graphView2 = new LineGraphView("Aggregate list", 380, 200, graphCons2, "Hours", "Items", false, false);
    if (statsTotal > 0) {
      double[] objTotalSeries = new double[statsTotal];
      double[] objAliveSeries = new double[statsTotal];
      double[] aggrTotalSeries = new double[statsTotal];
      double[] timeSeries = new double[statsTotal];
      for (int i=0; i<statsTotal; i++) {
        AggregationStatistics thisStats = statsHistory[(statsPtr + 2*STATS_HISTORY - (1+i))%STATS_HISTORY];
        objTotalSeries[i] = thisStats.numObjectsTotal;
        objAliveSeries[i] = thisStats.numObjectsAlive;
        aggrTotalSeries[i] = thisStats.numAggregatesTotal;
        timeSeries[i] = (statsTotal - (1+i)) * (((double)(STATS_SUBSAMPLE * UPDATE_TIME))/HOURS);
      }
      
      graphView2.addSeries("Objects total", timeSeries, objTotalSeries, Color.red);
      graphView2.addSeries("Objects alive", timeSeries, objAliveSeries, Color.green);
      graphView2.addSeries("Aggregates", timeSeries, aggrTotalSeries, Color.blue);
    }
      
    summaryPanel.addDataView(aggregationView);
    summaryPanel.addDataView(graphView);
    summaryPanel.addDataView(graphView2);

    return summaryPanel;
  }
  
  protected synchronized void updateData() {
    lastStats = aggregation.getStatistics();
    if ((--statsCounter) <= 0) {
      statsHistory[statsPtr] = lastStats;
      statsPtr = (statsPtr+1) % STATS_HISTORY;
      if (statsTotal < STATS_HISTORY)
        statsTotal ++;
      statsCounter = STATS_SUBSAMPLE;
    }
  }
}
