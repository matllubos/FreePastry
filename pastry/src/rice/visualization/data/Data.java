package rice.visualization.data;

import java.io.*;
import java.util.*;

import rice.p2p.commonapi.*;

public class Data implements Serializable {
  
  protected Vector panels;
  
  protected Hashtable views;
  
  public Data() {
    this.panels = new Vector();
    this.views = new Hashtable();
  }
  
  public void addDataPanel(DataPanel panel) {
    panels.addElement(panel);
  }
  
  public DataPanel[] getDataPanels() {
    return (DataPanel[]) panels.toArray(new DataPanel[0]);
  }
  
  protected void addView(String name, DataView view) {
    views.put(name, view);
  }
  
  public DataView getView(String name) {
    return (DataView) views.get(name);
  }
  
}