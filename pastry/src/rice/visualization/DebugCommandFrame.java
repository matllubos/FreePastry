package rice.visualization;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import rice.visualization.client.*;
import rice.pastry.dist.*;

public class DebugCommandFrame extends JFrame implements ActionListener {
  
  protected VisualizationClient client;
  
  protected JTextField requestField;
  
  protected JTextArea responseField;

  public void actionPerformed(ActionEvent e) {
    responseField.setText("debug> "+e.getActionCommand()+"\n"+client.executeCommand(e.getActionCommand()));
    requestField.setText("");
    requestField.requestFocus();
  }

  protected DebugCommandFrame(VisualizationClient client) {
    super(client.getAddress().toString()); 
    
    this.client = client;
    
    requestField = new JTextField();
    responseField = new JTextArea(16, 40);
    
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());
    panel.add(new JScrollPane(responseField), BorderLayout.CENTER);
    panel.add(requestField, BorderLayout.SOUTH);
    getContentPane().add(panel);
    
    pack();
    setVisible(true);
    requestField.requestFocus();
    requestField.addActionListener(this);
  }
}
