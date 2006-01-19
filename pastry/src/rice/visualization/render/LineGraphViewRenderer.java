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