package rice.visualization;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import rice.visualization.data.*;
import rice.visualization.render.*;
import rice.pastry.dist.*;

public class VisualizationFrame extends JFrame {
  
  protected Visualization visualization;
    
  protected PastryRingPanel pastryRingPanel;

  protected PastryNodePanel pastryNodePanel;
  
  protected InformationPanel informationPanel;
  
  protected ControlPanel controlPanel;
  
  protected VisualizationFrame(Visualization visualization) {
    super("Pastry Network Visualization"); 
    
    this.visualization = visualization;
    
    GridBagLayout layout = new GridBagLayout();
    GridBagConstraints c = new GridBagConstraints();
    
    getContentPane().setLayout(layout);
    
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    
    ViewRendererFactory factory = new ViewRendererFactory();
    factory.addRenderer(new TableViewRenderer(visualization));
    factory.addRenderer(new KeyValueListViewRenderer(visualization));
    factory.addRenderer(new LineGraphViewRenderer(visualization));
    factory.addRenderer(new PieChartViewRenderer(visualization));
        
    pastryRingPanel = new PastryRingPanel(visualization);
    c.gridx = 0;
    c.gridy = 0;
    c.gridheight = 3;
    c.gridwidth = 1;
    layout.setConstraints(pastryRingPanel, c);
    getContentPane().add(pastryRingPanel);
    
    c = new GridBagConstraints();
    
    pastryNodePanel = new PastryNodePanel(factory);
    c.gridx = 0;
    c.gridy = 3;
    c.gridheight = 1;
    c.gridwidth = 2;
    layout.setConstraints(pastryNodePanel, c);
    getContentPane().add(pastryNodePanel); 
    
    c = new GridBagConstraints();
    
    informationPanel = new InformationPanel(visualization);
    c.gridx = 1;
    c.gridy = 0;
    c.gridheight = 1;
    c.gridwidth = 1;
    layout.setConstraints(informationPanel, c);
    getContentPane().add(informationPanel); 
    
    c = new GridBagConstraints();
    
    controlPanel = new ControlPanel(visualization);
    c.gridx = 1;
    c.gridy = 1;
    c.gridheight = 2;
    c.gridwidth = 1;
    layout.setConstraints(controlPanel, c);
    getContentPane().add(controlPanel); 
    
    //Display the window.
    pack();
    setVisible(true);
  }
  
  public void nodeHighlighted(Node node) {
    pastryRingPanel.nodeHighlighted(node);
  }
  
  public void nodeSelected(Node node, Data data) {
    controlPanel.nodeSelected(node, data); 
    if (node != null) {
      if (node.ring == visualization.selectedRing)
        informationPanel.nodeSelected(node, data);
    } else {
      informationPanel.nodeSelected(node, data);      
    }
    pastryRingPanel.nodeSelected(node, data);
    pastryNodePanel.nodeSelected(node, data);
  }
}