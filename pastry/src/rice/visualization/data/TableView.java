package rice.visualization.data;

import java.util.*;

import rice.p2p.commonapi.*;

public class TableView extends DataView {
  
  protected Vector rows;
  
  protected int[] sizes;
    
  public TableView(String name, int width, int height, Constraints constraints) {
    super(name, width, height, constraints);
    
    this.rows = new Vector();
  }
  
  public void setSizes(int[] sizes) {
    this.sizes = sizes;
  }
  
  public void addRow(String[] items) {
    rows.add(items);
  }
  
  public int getRowCount() {
    return rows.size();
  }
  
  public int[] getSizes() {
    return sizes;
  }
  
  public String[] getRow(int index) {
    return (String[]) rows.elementAt(index);
  }
  
}

