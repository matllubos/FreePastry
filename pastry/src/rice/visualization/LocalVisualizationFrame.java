package rice.visualization;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import rice.visualization.data.*;
import rice.visualization.render.*;
import rice.pastry.dist.*;

public class LocalVisualizationFrame extends JFrame {
  
  protected PastryNodePanel pastryNodePanel;
  
  protected DataProvider visualization;
  
  protected LocalVisualizationFrame(DataProvider visualization) {
    super("Local Node Visualization"); 
    
    this.visualization = visualization;
    
    GridBagLayout layout = new GridBagLayout();
    GridBagConstraints c = new GridBagConstraints();
    
    getContentPane().setLayout(layout);
    
    ViewRendererFactory factory = ViewRendererFactory.buildFactory(visualization);

    pastryNodePanel = new PastryNodePanel(factory);
    layout.setConstraints(pastryNodePanel, c);
    getContentPane().add(pastryNodePanel); 
    
    //Display the window.
    pack();
    setVisible(true);
  }
  
  public void nodeSelected(Node node, Data data) {
    pastryNodePanel.nodeSelected(node, data); 
  }
}