package rice.visualization.server;

import rice.visualization.data.*;
import rice.pastry.*;
import rice.p2p.aggregation.*;
import rice.persistence.*;
import rice.selector.*;
import rice.Continuation.*;

import java.awt.*;
import java.util.*;

public class AggregationPanelCreator implements PanelCreator {
  
  public static final int UPDATE_TIME = 5000;
  public static final int AGGR_OBJ_MULTIPLIER = 12;
  public static final int WAITING_HISTORY = 240;
  public static final int AGGR_OBJ_HISTORY = 240;
  
  int[] waitingHistory = new int[WAITING_HISTORY];
  int[] aggregatesHistory = new int[AGGR_OBJ_HISTORY];
  int[] objectsHistory = new int[AGGR_OBJ_HISTORY];
  int waitingPtr = 0, aggrObjPtr = 0, seqNo = 0;
  AggregationImpl aggregation;
  
  public AggregationPanelCreator(rice.selector.Timer timer, AggregationImpl aggregation) {
    this.aggregation = aggregation;

    Arrays.fill(aggregatesHistory, aggregation.getNumAggregates());
    Arrays.fill(objectsHistory, aggregation.getNumObjectsInAggregates());

    timer.scheduleAtFixedRate(new rice.selector.TimerTask() {
      public void run() {
        updateData();
      }
    }, UPDATE_TIME, UPDATE_TIME);
  }
  
  public DataPanel createPanel(Object[] objects) {

    DataPanel summaryPanel = new DataPanel("Aggregation");

    GridBagConstraints cons = new GridBagConstraints();
    cons.gridx = 0;
    cons.gridy = 0;
    cons.fill = GridBagConstraints.HORIZONTAL;
    
    KeyValueListView aggregationView = new KeyValueListView("Aggregation Overview", 380, 200, cons);
    aggregationView.add("Obj. waiting", ""+aggregation.getNumObjectsWaiting());
    aggregationView.add("Aggr. total", ""+aggregation.getNumAggregates());
    aggregationView.add("Obj. in aggr", ""+aggregation.getNumObjectsInAggregates());
    aggregationView.add("Obj. store", aggregation.getObjectStore().getClass().getName());
    aggregationView.add("Aggr. store", aggregation.getAggregateStore().getClass().getName());
    
    rice.p2p.commonapi.Id currentHandle = (rice.p2p.commonapi.Id) aggregation.getHandle();
    aggregationView.add("Current root", ((currentHandle == null) ? "null" : ((rice.p2p.multiring.RingId)currentHandle).getId().toStringFull()));

    GridBagConstraints graphCons = new GridBagConstraints();
    graphCons.gridx = 1;
    graphCons.gridy = 0;
    graphCons.fill = GridBagConstraints.HORIZONTAL;

    LineGraphView graphView = new LineGraphView("Waiting list", 380, 200, graphCons, "Seconds", "Objects", false, false);
    double[] waitingSeries = new double[WAITING_HISTORY];
    double[] timeSeries = new double[WAITING_HISTORY];
    for (int i=0; i<WAITING_HISTORY; i++) {
      waitingSeries[i] = waitingHistory[(waitingPtr+i) % WAITING_HISTORY];
      timeSeries[i] = (i*UPDATE_TIME)/1000;
    }
    graphView.addSeries("Waiting", timeSeries, waitingSeries, Color.blue);

    GridBagConstraints graphCons2 = new GridBagConstraints();
    graphCons2.gridx = 2;
    graphCons2.gridy = 0;
    graphCons2.fill = GridBagConstraints.HORIZONTAL;

    LineGraphView graphView2 = new LineGraphView("Aggregate list", 380, 200, graphCons2, "Minutes", "Objects / Aggregates", false, false);
    double[] aggrCreatedSeries = new double[AGGR_OBJ_HISTORY-1];
    double[] avgSizeSeries = new double[AGGR_OBJ_HISTORY-1];
    double[] timeSeries2 = new double[AGGR_OBJ_HISTORY-1];
    for (int i=0; i<AGGR_OBJ_HISTORY-1; i++) {
      int aggrsCreated = aggregatesHistory[(aggrObjPtr+i+1)%AGGR_OBJ_HISTORY]-aggregatesHistory[(aggrObjPtr+i)%AGGR_OBJ_HISTORY];
      int objsCreated = objectsHistory[(aggrObjPtr+i+1)%AGGR_OBJ_HISTORY]-objectsHistory[(aggrObjPtr+i)%AGGR_OBJ_HISTORY];
      aggrCreatedSeries[i] = aggrsCreated;
      avgSizeSeries[i] = (aggrsCreated>0) ? (((double)objsCreated)/aggrsCreated) : 0;
      timeSeries2[i] = (i*UPDATE_TIME*AGGR_OBJ_MULTIPLIER)/60000;
    }
    graphView2.addSeries("Aggregates created", timeSeries2, aggrCreatedSeries, Color.red);
    graphView2.addSeries("Avg objects/aggregate", timeSeries2, avgSizeSeries, Color.green);
      
    summaryPanel.addDataView(aggregationView);
    summaryPanel.addDataView(graphView);
    summaryPanel.addDataView(graphView2);

    return summaryPanel;
  }
  
  protected synchronized void updateData() {
    waitingHistory[waitingPtr] = aggregation.getNumObjectsWaiting();
    waitingPtr = (waitingPtr+1) % WAITING_HISTORY;

    if (((++seqNo) % AGGR_OBJ_MULTIPLIER) == 0) {
      aggregatesHistory[aggrObjPtr] = aggregation.getNumAggregates();
      objectsHistory[aggrObjPtr] = aggregation.getNumObjectsInAggregates();
      aggrObjPtr = (aggrObjPtr+1) % AGGR_OBJ_HISTORY;
    }
  }
}
