package rice.visualization.server;

import rice.visualization.data.*;
import rice.pastry.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.persistence.*;
import rice.Continuation.*;
import rice.selector.*;

import java.awt.*;
import java.util.*;

public class MultiPASTPanelCreator implements PanelCreator {

  PASTPanelCreator[] creators;
  
  public MultiPASTPanelCreator(rice.selector.Timer timer, String[] names, PastImpl[] pasts) {
    creators = new PASTPanelCreator[names.length];
    
    for (int i=0; i<names.length; i++)
      creators[i] = new PASTPanelCreator(timer, names[i], pasts[i]);
  }
  
  public DataPanel createPanel(Object[] objects) {
    MultiDataPanel panel = new MultiDataPanel("PAST");
    
    for (int i=0; i<creators.length; i++)
      panel.addDataPanel(creators[i].createPanel(objects));
    
    return panel;
  }
}
