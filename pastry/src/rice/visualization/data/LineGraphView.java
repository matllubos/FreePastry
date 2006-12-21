/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/
package rice.visualization.data;

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
  
  public LineGraphView(String name, int width, int height, Constraints constraints, String xLabel, String yLabel, boolean area, boolean legend) {
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

