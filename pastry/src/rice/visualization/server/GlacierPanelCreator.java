package rice.visualization.server;

import rice.visualization.data.*;
import rice.pastry.*;
import rice.p2p.glacier.v1.*;
import rice.persistence.*;
import rice.Continuation.*;

import java.awt.*;
import java.util.*;

public class GlacierPanelCreator implements PanelCreator {
  
  public static int UPDATE_TIME = 1000;
  
  Vector data = new Vector();

  Vector cache = new Vector();
  
  Vector times = new Vector();
  
  protected StorageManager manager;
  
  public GlacierPanelCreator() {
    Thread t = new Thread("Glacier Panel Monitor Thread") {
      public void run() {
        while (true) {
          try {
            updateData();
            Thread.currentThread().sleep(UPDATE_TIME);
          } catch (InterruptedException e) {
          }
        }
      }
    };
      
    t.start();
  }
  
  public DataPanel createPanel(Object[] objects) {
    PastryNode node = null;
    GlacierImpl glacier = null;
    
    for (int i=0; i<objects.length; i++) {
      if (objects[i] instanceof PastryNode)
        node = (PastryNode) objects[i];
      else if (objects[i] instanceof rice.p2p.glacier.v1.GlacierImpl)
        glacier = (GlacierImpl) objects[i];
      else if (objects[i] instanceof StorageManager)
        manager = (StorageManager) objects[i];
      
      if ((node != null) && (glacier != null) && (manager != null))
        return createPanel(node, glacier, manager);
    }
    
    return null;
  }
  
  protected DataPanel createPanel(PastryNode node, GlacierImpl glacier, StorageManager manager) {

    DataPanel glacierPanel = new DataPanel("Glacier");

/*
    GlacierState state = glacier.getState();
    int numFragments = glacier.getNumFragments();
    
    GridBagConstraints glacierCons = new GridBagConstraints();
    glacierCons.gridx = 0;
    glacierCons.gridy = 0;
    glacierCons.fill = GridBagConstraints.HORIZONTAL;
    
    KeyValueListView glacierView = new KeyValueListView("Glacier Overview", 380, 200, glacierCons);
    glacierView.add("Last SC", ""+state.lastStatusCast);
    glacierView.add("Seq#", ""+state.currentSequenceNo);
    glacierView.add("Holders", ""+state.holderList.size());
    glacierView.add("Files", ""+state.fileList.size());
    glacierView.add("History", ""+state.history.size()+" entries");
    glacierView.add("Local time", ""+(new Date()));
    glacierView.add("Next SC", ""+glacier.getNextStatusCastDate());
    
    int numOwn = 0, numCertain = 0, numProbable = 0, numDead = 0, numDuplicates = 0;
    double[] fragmentHisto = new double[numFragments + 10];
    double[] timeValues = new double[numFragments + 10];
    for (int i=0; i<(numFragments+10); i++) {
        fragmentHisto[i] = 0;
        timeValues[i] = i;
    }
    
    Enumeration enu = state.fileList.elements();
    while (enu.hasMoreElements()) {
        FileInfo fileInfo = (FileInfo)enu.nextElement();
        int liveFragments = 0;
        for (int i=0; i<numFragments; i++) {
            boolean certainKnown = false;
            for (int j=0; j<FileInfo.maxHoldersPerFragment; j++) {
                if (fileInfo.holderKnown[i][j]) {
                    if (fileInfo.holderCertain[i][j]) {
                        if (!fileInfo.holderDead[i][j]) {
                            if (certainKnown)
                                numDuplicates ++;
                            certainKnown = true;
                            if (fileInfo.holderId[i][j] == null)
                                numOwn ++;
                            else
                                numCertain ++;
                        } else numDead ++;
                    } else {
                        if (fileInfo.holderDead[i][j])
                            numDead ++;
                        else
                            numProbable ++;
                    }
                }
            }
            
            if (certainKnown)
                liveFragments ++;
        }
        
        if (liveFragments > (numFragments+9))
            liveFragments = numFragments+9;
            
        fragmentHisto[liveFragments] ++;
    }

    GridBagConstraints glacierCons2 = new GridBagConstraints();
    glacierCons2.gridx = 1;
    glacierCons2.gridy = 0;
    glacierCons2.fill = GridBagConstraints.HORIZONTAL;

    KeyValueListView glacierView2 = new KeyValueListView("Glacier Details", 380, 200, glacierCons2);
    glacierView2.add("F.Local", ""+numOwn);
    glacierView2.add("F.Certain", ""+numCertain);
    glacierView2.add("F.Uncertain", ""+numProbable);
    glacierView2.add("F.Dead", ""+numDead);
    glacierView2.add("F.Duplicate", ""+numDuplicates);
    glacierView.add("Files @"+numFragments, ""+((int)fragmentHisto[numFragments]));

    GridBagConstraints fragmentHistoCons = new GridBagConstraints();
    fragmentHistoCons.gridx = 2;
    fragmentHistoCons.gridy = 0;
    fragmentHistoCons.fill = GridBagConstraints.HORIZONTAL;

    LineGraphView fragmentHistoView = new LineGraphView("Fragment histogram", 380, 200, fragmentHistoCons, "# Live fragments", "Files", false);
    fragmentHisto[numFragments] = 0;
    fragmentHistoView.addSeries("Data Stored", timeValues, fragmentHisto, Color.green);
      
    glacierPanel.addDataView(glacierView);
    glacierPanel.addDataView(glacierView2);
    glacierPanel.addDataView(fragmentHistoView);

*/        
    return glacierPanel;
  }
  
  protected synchronized void updateData() {
    if (manager != null) {
      /* do nothing */
    }
  }
}
