package rice.visualization.render;

import java.awt.*;
import java.awt.image.*;
import java.util.*;
import javax.swing.*;

import org.jfree.data.*;
import org.jfree.chart.*;
import org.jfree.chart.plot.*;

import rice.visualization.*;
import rice.visualization.data.*;

public class PieChartViewRenderer extends ViewRenderer {
  
  public PieChartViewRenderer(DataProvider visualization) {
    super(visualization);
  }  
  
  public boolean canRender(DataView view) {
    return (view instanceof PieChartView);
  }
  
  public JPanel render(final DataView v) {
    JPanel panel = new JPanel() {
      public Dimension getPreferredSize() {
        return new Dimension(v.getWidth(), v.getHeight());
      }
      
      public void paintComponent(Graphics g) {
        PieChartView view = (PieChartView) visualization.getData().getView(v.getName());        
        DefaultPieDataset data = new DefaultPieDataset();
        
        for (int i=0; i<view.getItemCount(); i++) {
          data.setValue(view.getLabel(i), view.getValue(i));
        }
        
        BufferedImage image = createPieGraph(data, view.getWidth() - 2*getDefaultBorder(), view.getHeight() - 2*getDefaultBorder());
        g.drawImage(image, getDefaultBorder(), getDefaultBorder(), null);
      }
    }; 
    
    panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(v.getName()),
                                                       BorderFactory.createEmptyBorder(5,5,5,5)));
    return panel; 
  }
  
  public BufferedImage createPieGraph(PieDataset set, int width, int height) {
    JFreeChart chart =
    ChartFactory.createPieChart(null, set, false, false, false);
    
    PiePlot plot = (PiePlot) chart.getPlot();
    plot.setSectionLabelFont(new Font("Courier", Font.PLAIN, 10));
    plot.setSeriesLabelFont(new Font("Courier", Font.PLAIN, 10));
    
    BufferedImage image = chart.createBufferedImage(width, height);
    return image;   
  }
}
