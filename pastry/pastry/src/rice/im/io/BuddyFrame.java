
package rice.im.io;



import java.awt.*;
import java.awt.event.*;
import javax.swing.*;


import rice.im.io.*;


public class BuddyFrame extends JInternalFrame {
   BuddyPanel _panel;

	public BuddyFrame(IMGui gui) {
      super("Favorites",true,true,true,true);
      getContentPane().setLayout(new BorderLayout());
      _panel = new BuddyPanel(gui);
      getContentPane().add(_panel,BorderLayout.CENTER);
      setVisible(true);
	}
   

    public BuddyPanel getPanel() {
	return _panel;
    }
}
