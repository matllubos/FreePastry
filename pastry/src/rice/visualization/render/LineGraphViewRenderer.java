/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
package rice.visualization.render;

import java.awt.*;
import java.awt.image.*;
import java.util.*;
import javax.swing.*;

import org.jfree.data.*;
import org.jfree.chart.*;
import org.jfree.chart.renderer.*;
import org.jfree.chart.plot.*;

import rice.visualization.*;
import rice.visualization.data.*;

public class LineGraphViewRenderer extends ViewRenderer {
  
  public LineGraphViewRenderer(DataProvider visualization) {
    super(visualization);
  }  
  
  public boolean canRender(DataView view) {
    return (view instanceof LineGraphView);
  }
  
  public JPanel render(final DataView v) {
    JPanel panel = new JPanel() {
      public Dimension getPreferredSize() {
        return new Dimension(v.getWidth(), v.getHeight());
      }
      
      public void paintComponent(Graphics g) {
        if (visualization.getData() != null) {
        LineGraphView view = (LineGraphView) visualization.getData().getView(v.getName());
        if (view == null)
          return;
        
        XYSeries[] data = new XYSeries[view.getSeriesCount()];
        java.awt.Color[] colors = new java.awt.Color[view.getSeriesCount()];
        
        for (int i=0; i<view.getSeriesCount(); i++) {
          double[] domain = view.getDomain(i);
          double[] range = view.getRange(i);
          
          data[i] = new XYSeries(view.getLabel(i));
          colors[i] = view.getColor(i).trans();
          
          for (int j=0; j<domain.length; j++)
            data[i].add(domain[j], range[j]);
        }
        
        BufferedImage image = createGraph(data, view.getXLabel(), view.getYLabel(), view.getWidth() - 2*getDefaultBorder(), view.getHeight() - 2*getDefaultBorder(), colors, view.getArea(), view.getLegend());
        g.drawImage(image, getDefaultBorder(), getDefaultBorder(), null);
        }
      }
    }; 
    
    panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(v.getName()),
                                                       BorderFactory.createEmptyBorder(5,5,5,5)));
    return panel; 
  }
  
  public BufferedImage createGraph(XYSeries[] series, String xLabel, String yLabel, int width, int height, java.awt.Color[] color, boolean area, boolean legend) {
    XYSeriesCollection dataset= new XYSeriesCollection();
    
    for (int i=0; i<series.length; i++)
      dataset.addSeries(series[i]);
    
    JFreeChart chart =
      ChartFactory.createXYLineChart(null, xLabel, yLabel, dataset, PlotOrientation.VERTICAL, legend, false, false);
    
    XYPlot plot = chart.getXYPlot();
    
    if (area)
      plot.setRenderer(new XYAreaRenderer(XYAreaRenderer.AREA));
    
    plot.getRangeAxis().setLabelFont(new Font("Courier", Font.PLAIN, 10));
    plot.getRangeAxis().setTickLabelFont(new Font("Courier", Font.PLAIN, 10));
    plot.getDomainAxis().setLabelFont(new Font("Courier", Font.PLAIN, 10));
    plot.getDomainAxis().setTickLabelFont(new Font("Courier", Font.PLAIN, 10));
    
    for (int i=0; i<color.length; i++)
      plot.getRenderer().setSeriesPaint(i, color[i]);
    
    BufferedImage image = chart.createBufferedImage(width, height);
    return image;
  }  
}