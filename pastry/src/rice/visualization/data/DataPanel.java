package rice.visualization.data;

import java.io.*;
import java.util.*;

import rice.p2p.commonapi.*;

public class DataPanel implements Serializable {
  
  protected String name;
  
  protected Vector views;
  
  public DataPanel(String name) {
    this.name = name;
    this.views = new Vector();
  }
  
  public String getName() {
    return name;
  }
  
  public void addDataView(DataView view) {
    views.addElement(view);
  }
  
  public DataView getDataView(int i) {
    return (DataView) views.elementAt(i);
  }
  
  public int getDataViewCount() {
    return views.size();
  }
  
}