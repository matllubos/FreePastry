package rice.visualization;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import rice.visualization.data.*;
import rice.visualization.render.*;
import rice.pastry.dist.*;

public class LocalVisualizationFrame extends JFrame {
  
  protected PastryNodePanel pastryNodePanel;
  
  protected LocalVisualization visualization;
  
  protected LocalVisualizationFrame(LocalVisualization visualization) {
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
    
    addWindowListener(new WindowListener() {
  public void windowActivated(WindowEvent e) {}      
  public void windowClosed(WindowEvent e) {
  LocalVisualizationFrame.this.visualization.exit();
  }      
      public void windowClosing(WindowEvent e) {
        LocalVisualizationFrame.this.visualization.exit();
      }      
  public void windowDeactivated(WindowEvent e) {}      
  public void windowDeiconified(WindowEvent e) {}      
  public void windowIconified(WindowEvent e) {}      
  public void windowOpened(WindowEvent e) {}
    });
      
  }
  
  public void nodeSelected(Node node, Data data) {
    pastryNodePanel.nodeSelected(node, data); 
  }
}