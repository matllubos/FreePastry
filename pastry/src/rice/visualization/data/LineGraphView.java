package rice.visualization.data;

import java.awt.*;
import java.util.*;

import rice.p2p.commonapi.*;

public class LineGraphView extends DataView {
  
  protected Vector domains;
  
  protected Vector ranges;
  
  protected Vector labels;
  
  protected String xLabel;
  
  protected String yLabel;
  
  protected Vector colors;
  
  protected boolean area;
  
  protected boolean legend;
  
  public LineGraphView(String name, int width, int height, GridBagConstraints constraints, String xLabel, String yLabel, boolean area, boolean legend) {
    super(name, width, height, constraints);
    
    this.labels = new Vector();
    this.domains = new Vector();
    this.ranges = new Vector();
    this.colors = new Vector();
    this.xLabel = xLabel;
    this.yLabel = yLabel;
    this.area = area;
    this.legend = legend;
  }
  
  public void addSeries(String name, double[] domain, double[] range, Color color) {
    labels.add(name);
    domains.add(domain);
    ranges.add(range);
    colors.add(color);
  }
  
  public int getSeriesCount() {
    return domains.size();
  }
  
  public double[] getDomain(int index) {
    return (double[]) domains.elementAt(index);
  }
  
  public String getLabel(int index) {
    return (String) labels.elementAt(index);
  }
  
  public Color getColor(int index) {
    return (Color) colors.elementAt(index);
  }
  
  public double[] getRange(int index) {
    return (double[]) ranges.elementAt(index);
  }
  
  public String getXLabel() {
    return xLabel;
  }
  
  public String getYLabel() {
    return yLabel;
  }
  
  public boolean getArea() {
    return area;
  }
  
  public boolean getLegend() {
    return legend;
  }
}

