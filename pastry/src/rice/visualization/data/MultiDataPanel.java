package rice.visualization.data;

import java.io.*;
import java.util.*;

import rice.p2p.commonapi.*;

public class MultiDataPanel extends DataPanel {
  
  protected Vector panels = new Vector();
  
  public MultiDataPanel(String name) {
    super(name);
  }
  
  public void addDataPanel(DataPanel panel) {
    panels.add(panel);
  }
  
  public DataPanel[] getDataPanels() {
    return (DataPanel[]) panels.toArray(new DataPanel[0]);
  }
  
  public void addDataView(DataView view) {
    throw new IllegalArgumentException("MONKEY");
  }
  
  public DataView getDataView(int i) {
    throw new IllegalArgumentException("MONKEY");
  }
  
  public int getDataViewCount() {
    throw new IllegalArgumentException("MONKEY");
  }
  
}