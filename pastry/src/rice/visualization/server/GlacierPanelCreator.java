package rice.visualization.server;

import rice.visualization.data.*;
import rice.pastry.*;
import rice.p2p.glacier.v2.*;
import rice.p2p.multiring.RingId;
import rice.persistence.*;
import rice.Continuation.*;

import java.awt.*;
import java.util.*;

public class GlacierPanelCreator implements PanelCreator, GlacierStatisticsListener {

  private static final int HISTORY = 300;
  private final long startup = System.currentTimeMillis();
  
  GlacierImpl glacier;
  GlacierStatistics history[] = new GlacierStatistics[HISTORY];
  int msgTotal[] = new int[20];
  int msgSent = 0;
  int historyPtr = 0;
  
  public GlacierPanelCreator(GlacierImpl glacier) {
    this.glacier = glacier;
    glacier.addStatisticsListener(this);
    Arrays.fill(msgTotal, 0);
    for (int i=0; i<HISTORY; i++)
      history[i] = null;
  }
  
  public DataPanel createPanel(Object[] objects) {

    DataPanel glacierPanel = new DataPanel("Glacier");

    GridBagConstraints glacierCons = new GridBagConstraints();
    glacierCons.gridx = 0;
    glacierCons.gridy = 0;
    glacierCons.fill = GridBagConstraints.HORIZONTAL;
    
    KeyValueListView glacierView = new KeyValueListView("Glacier Overview", 380, 200, glacierCons);
    GlacierStatistics current = history[(historyPtr + HISTORY - 1) % HISTORY];
    if (current != null) {
      rice.p2p.commonapi.IdRange responsibleRange = current.responsibleRange;
      rice.p2p.commonapi.Id ccwBoundary = responsibleRange.getCCWId();
      rice.p2p.commonapi.Id cwBoundary = responsibleRange.getCWId();
      
      if (ccwBoundary instanceof RingId)
        ccwBoundary = ((RingId)ccwBoundary).getId();
      if (cwBoundary instanceof RingId)
        cwBoundary = ((RingId)cwBoundary).getId();
        
      long uptime = System.currentTimeMillis() - startup;
      long upMin = uptime / (60*1000);
      long upHours = upMin / 60;
      long upDays = upHours / 24;
        
      glacierView.add("Pending req", "" + current.pendingRequests);
      glacierView.add("Neighbors", "" + current.numNeighbors);
      glacierView.add("Fragments", "" + current.numFragments);
      glacierView.add("Continuations", "" + current.numContinuations);
      glacierView.add("Objs in trash", "" + current.numObjectsInTrash);
      glacierView.add("Msg sent", "" + msgSent);
      glacierView.add("Range", ccwBoundary + " - " + cwBoundary);
      glacierView.add("Local time", (new Date()).toString());
      glacierView.add("Uptime", upDays+"d "+(upHours%24)+"h "+(upMin%60)+"m");
      glacierView.add("Fragm. total", "" + current.fragmentStorageSize + " bytes");
      glacierView.add("Trash total", "" + current.trashStorageSize + " bytes");
    } else {
      glacierView.add("Starting up...", "");
    }

    GridBagConstraints countCons = new GridBagConstraints();
    countCons.gridx = 1;
    countCons.gridy = 0;
    countCons.fill = GridBagConstraints.HORIZONTAL;
    KeyValueListView countView = new KeyValueListView("Message count", 380, 200, countCons);

    GridBagConstraints graphCons = new GridBagConstraints();
    graphCons.gridx = 2;
    graphCons.gridy = 0;
    graphCons.fill = GridBagConstraints.HORIZONTAL;
    LineGraphView graphView = new LineGraphView("Messages", 380, 200, graphCons, "Minutes", "Messages", false, false);

    String tagToString[] = new String[] { "--", "Neighbor", "Sync", "SyncManifests", "SyncFetch", "Handoff", "Debug", "Refresh", "Insert", "LookupHandles", "Lookup", "Fetch", "LocalScan" };
    Color tagToColor[] = new Color[] { Color.white, Color.green, Color.blue, Color.black, Color.cyan, Color.darkGray, Color.lightGray, Color.magenta, Color.orange, Color.pink, Color.red, Color.yellow, Color.gray };
    double[] timeSeries = new double[HISTORY];
    for (int i=1; i<=12; i++) {
      countView.add(tagToString[i], (current == null) ? "?" : ""+current.messagesSentByTag[i]+" ("+msgTotal[i]+" total)");
      double[] countSeries = new double[HISTORY];
      for (int j=0; j<HISTORY; j++) {
        GlacierStatistics statJ = history[(historyPtr+j) % HISTORY];
        timeSeries[j] = j;
        countSeries[j] = (statJ == null) ? 0 : statJ.messagesSentByTag[i];
      }
      
      graphView.addSeries("Tag "+i, timeSeries, countSeries, tagToColor[i]);
    }
      
    glacierPanel.addDataView(glacierView);
    glacierPanel.addDataView(countView);
    glacierPanel.addDataView(graphView);

    return glacierPanel;
  }
  
  public void receiveStatistics(GlacierStatistics stat) {
    history[historyPtr] = stat;
    historyPtr = (historyPtr+1) % HISTORY;
    for (int i=0; i<stat.messagesSentByTag.length; i++) {
      msgTotal[i] += stat.messagesSentByTag[i];
      msgSent += stat.messagesSentByTag[i];
    }
  }
}
