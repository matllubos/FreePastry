/*
 * Created on Mar 12, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package rice.visualization;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.filechooser.FileFilter;

import rice.pastry.dist.DistNodeHandle;
import rice.visualization.client.UpdateJarResponse;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class ControlPanel extends JPanel implements ActionListener {
  
  protected Visualization visualization;

  JButton selectJarsButton;

  JPanel centerPanel;
  JList fileList; 
  JButton removeJarsButton;

  JPanel southPanel;
  JTextField commandLineField;  
  JButton updateJarsButton;

  JFileChooser chooser;
  DefaultListModel selectedFiles;

  /**
   * 
   */
  public ControlPanel(Visualization visualization) {
    this.visualization = visualization;
    setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Node Control"),
                                               BorderFactory.createEmptyBorder(5,5,5,5)));
    this.setLayout(new BorderLayout());
  
    selectJarsButton = new JButton("Add Jars");

    centerPanel = new JPanel();
    centerPanel.setLayout(new BorderLayout());

//    jarsPane = new JTextPane();
    selectedFiles = new DefaultListModel();
/*
    selectedFiles.addElement("one");
    selectedFiles.addElement("one afffasfdadsdafj;flkjafds;lajfasd asdf fadsfa falasdjfads fjas");
    selectedFiles.addElement("one asdfa");
    selectedFiles.addElement("one erwqrqw");
    selectedFiles.addElement("one yert");
    selectedFiles.addElement("oneasfdae3q");
    selectedFiles.addElement("one qwerqrew");
    selectedFiles.addElement("onesdajd");
    selectedFiles.addElement("oneqwer");
*/
    fileList = new JList(selectedFiles);
    removeJarsButton = new JButton("Remove Jars");
    
    
//    fileList.setListData(selectedFiles);
    JScrollPane jarsPane = new JScrollPane(); //fileList);
    jarsPane.getViewport().setView(fileList); 

    centerPanel.add(jarsPane,BorderLayout.CENTER);
    centerPanel.add(removeJarsButton,BorderLayout.SOUTH);
 
    southPanel = new JPanel();
    southPanel.setLayout(new BorderLayout());

    commandLineField = new JTextField();
    updateJarsButton = new JButton("Update and Restart");
    updateJarsButton.setEnabled(false);
    commandLineField.setBorder(
      BorderFactory.createCompoundBorder(
      BorderFactory.createTitledBorder("Command Line"),
      BorderFactory.createEmptyBorder(5,5,5,5)));
    southPanel.add(commandLineField,BorderLayout.CENTER);
    southPanel.add(updateJarsButton,BorderLayout.SOUTH);

    add(selectJarsButton,BorderLayout.NORTH);  
    add(centerPanel,BorderLayout.CENTER);                  
    add(southPanel,BorderLayout.SOUTH);                    
  
    selectJarsButton.addActionListener(this);
    updateJarsButton.addActionListener(this);
    removeJarsButton.addActionListener(this);

    chooser = new JFileChooser();
    JarFileFilter filter = new JarFileFilter();
    chooser.setFileFilter(filter);
    chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    chooser.setApproveButtonText("Add");
    chooser.setMultiSelectionEnabled(true);

  }

  class JarFileFilter extends FileFilter {
    public boolean accept(File arg0) {
      return arg0.getName().endsWith(".jar");
    }

    public String getDescription() {
      return "Jar Files";    
    }
  }

  public Dimension getPreferredSize() {
    return new Dimension(InformationPanel.INFORMATION_PANEL_WIDTH, InformationPanel.INFORMATION_PANEL_HEIGHT);
  }

  public void nodeSelected(DistNodeHandle node) {
    if (node != null) {
      updateJarsButton.setEnabled(true);      
    } else {
      updateJarsButton.setEnabled(false);
    }
  }

  public void actionPerformed(ActionEvent arg0) {
//    System.out.println(arg0.getSource());
    if (arg0.getSource() == selectJarsButton) {
      int returnVal = chooser.showOpenDialog(this);
      if(returnVal == JFileChooser.APPROVE_OPTION) {
        addFiles(chooser.getSelectedFiles());
      }      
    }

    if (arg0.getSource() == removeJarsButton) {
      Object[] o = fileList.getSelectedValues();
      System.out.println("o.length"+o.length);
      if (o.length > 0) {
        removeFiles(o);
      } else {
        // TODO: Tell user to select files to delete
      }
    }

    if (arg0.getSource() == updateJarsButton) {
      int resp = JOptionPane.showConfirmDialog(this,"Are you sure you want to restart this node?","Restart Node",JOptionPane.YES_NO_OPTION);
      if (resp == JOptionPane.YES_OPTION) {
        int size = selectedFiles.getSize();
        File[] files = new File[size];
        for (int i = 0; i < size; i++) {
          files[i] = (File)selectedFiles.get(i);
        }
        String text = commandLineField.getText();
        if (text.equals("")) {
          text = null;
        }
        UpdateJarResponse ujr = visualization.updateJar(files,text);
        if (ujr.success()) {
          switch(ujr.getResponse()) {
            case UpdateJarResponse.OK:
              JOptionPane.showMessageDialog(this, "Transfer Successful, Restarting...",
                       "Success", JOptionPane.INFORMATION_MESSAGE);                  
              break;
            case UpdateJarResponse.FILE_COPY_NOT_ALLOWED:
              JOptionPane.showMessageDialog(this, "Not Allowed To Update Jars", "Error", JOptionPane.ERROR_MESSAGE);          
              break;
            case UpdateJarResponse.NEW_EXEC_NOT_ALLOWED:
              JOptionPane.showMessageDialog(this, "Not Allowed To Change Execution Command, Restarting...", "Warning", JOptionPane.WARNING_MESSAGE);          
              break;
          }
        } else {
          JOptionPane.showMessageDialog(this, ujr.getException(), "Error", JOptionPane.ERROR_MESSAGE);
        }      
      }
    }
  }
  
  public void addFiles(File[] newFiles) {
    if (newFiles == null) return;
    for (int i = 0; i < newFiles.length; i++) {
      if (!selectedFiles.contains(newFiles[i])) {
        selectedFiles.addElement(newFiles[i]);    
      }
    }
  }

  public void removeFiles(Object[] newFiles) {
    System.out.println("removeFiles()");
    if (newFiles == null) return;
    for (int i = 0; i < newFiles.length; i++) {
      selectedFiles.removeElement((File)newFiles[i]);    
    }    
  }
}
