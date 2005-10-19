
package rice.im.io;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;

import rice.im.*;


public class ChatFrame extends JInternalFrame {
    private ChatPanel _chatPanel = null;
    private String prefix = "/home/anwisdas/pastry/src/rice/im/io/images/";

   public ChatPanel getChatPanel() {return _chatPanel;}

   public ChatFrame(ChatPanel consolePanel) {
      // closable, maximizable, iconifiable, resizable
      super("Console",true,true,true,true);
      setFrameIcon(new ImageIcon(prefix + "Inform.java"));


      _chatPanel = consolePanel;
      
      getContentPane().setLayout(new BorderLayout());
      getContentPane().add(_chatPanel,BorderLayout.CENTER);

      

      // Add listener so we can become invisible on close
      addInternalFrameListener( new InternalFrameAdapter() {
	      public void internalFrameClosing(InternalFrameEvent e) {

		  SwingUtilities.invokeLater(new Runnable() {
			  public void run() {
			      
			  }
		      });
		  
	      }
	  });

      setVisible(true);
   }

   

   public JInternalFrame getFrame() {
      return this;
   }
}








