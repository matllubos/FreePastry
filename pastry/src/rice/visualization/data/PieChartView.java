package rice.visualization.data;

import java.awt.*;
import java.util.*;

import rice.p2p.commonapi.*;

public class PieChartView extends DataView {
    
  protected Vector items;
  
  protected Vector labels;
  
  public PieChartView(String name, int width, int height, GridBagConstraints constraints) {
    super(name, width, height, constraints);
    
    this.items = new Vector();
    this.labels = new Vector();
  }
  
  public void addItem(String name, double value) {
    labels.add(name);
    items.add(new Double(value));
  }
  
  public int getItemCount() {
    return items.size();
  }
  
  public double getValue(int index) {
    return ((Double) items.elementAt(index)).doubleValue();
  }
  
  public String getLabel(int index) {
    return (String) labels.elementAt(index);
  }
  
}

