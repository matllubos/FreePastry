
package rice.im.io;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;

import java.util.*;
import java.io.*;

import rice.im.*;
import rice.im.log.*;
import rice.im.messaging.*;


public class ChatPanel extends JPanel  {

  
   protected Container   _container = null;
   private JScrollPane   _scrollpane = null;
   private JTextPane     _display = null;
    private StyleContext  _styles = new StyleContext();
   private JTextField    _input = null;
   private int           _adjustsb=0;
  
    private boolean       _isConsole = false;
    private UserNode      _node;
    private static ChatPanel     _chatpanel;
    String my_name = IMGui.getIMGui().getClient().getService().pua.getName(); // NOTE: This assumes that IMGui begins after IMService and IMClient. May change in the future.
    String transcript;
   //------------------------------------------------------------------
   /** For chat console */
   ChatPanel(Container container, UserNode node) {
       
       _container = container;
       _node = node;
       _chatpanel = this;

       
       
       
       
       setLayout(new BorderLayout());

      // Add scrollable text pane for display of incoming messages
      _display = new JTextPane();
      _display.setDocument(new DefaultStyledDocument());
      
      _display.setBackground(Color.white);
      
      if (IMGui.getIMGui().open_conn.get(_node.getName()) != null) {
	  
	  Vector vec = (Vector) IMGui.getIMGui().open_conn.get(_node.getName());
	  transcript = (String) vec.get(1);
	  _display.setText(transcript);
	  System.out.println("Transcript = " + transcript);
      }
      
      _scrollpane = new JScrollPane(_display,
				    ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
				    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER );
      add(_scrollpane,BorderLayout.CENTER);
      
      // Add text field for command/message entry
      add( _input = new JTextField(),BorderLayout.SOUTH);
      
      
      
      _input.addActionListener( new ActionListener() {
	      
		  public void actionPerformed( ActionEvent e ) {
		      System.out.println("Message about to be sent");
		      final Buddy recipients[] = new Buddy[1];
		      recipients[0] = new Buddy(_node.getName(), null);
		      final IMTextMessage itm = new IMTextMessage(IMGui.getIMGui().getClient().getService().pua, recipients, _input.getText(), new IMState(IMState.ONLINE));
		      IMGui.getIMGui().getClient().getService().sendMessage(itm);
		      
		  Vector vec = (Vector) IMGui.getIMGui().open_conn.get(_node.getName());
		  transcript = (String) vec.get(1);
		  String temp = transcript + "\n" + my_name + ": " + _input.getText();
		  vec.set(1, temp);
		  System.out.println("Vector contents in input are " + ( (String) vec.get(1)));
		  IMGui.getIMGui().open_conn.put(_node.getName(), vec);
		  
		  _display.setText(transcript + "\n" + my_name + ": " + _input.getText());
		  _input.setText("");
		  _adjustsb = 5;
		  
		  
		  }
	  });    
   }

    public static ChatPanel getChatPanel() {
	return _chatpanel;
    }
   //------------------------------------------------------------------
   public void addMouseListener(MouseListener listener) {
      _display.addMouseListener(listener);
   }
   //------------------------------------------------------------------
   public void removeMouseListener(MouseListener listener) {
      _display.removeMouseListener(listener);
   }
   //------------------------------------------------------------------
   public void println(String txt, String style) {
      print(txt+"\n",style);
   }
   //------------------------------------------------------------------
   public void print(String txt, String style) {

      Document doc = _display.getDocument();
      try {
         
      }
      catch (Exception e) {}

      // Force scrollpane to bottom of display, Part 1/3
		JScrollBar sb = _scrollpane.getVerticalScrollBar();
      int max = sb.getMaximum();
      int ext = sb.getModel().getExtent();
      sb.setValue(max-ext);

      // Force scrollpane to bottom of display, Part 2/3
      _adjustsb = 5;
   }
   //------------------------------------------------------------------
   public void shutdown() {
      _container.remove(this);
   }
  
   //------------------------------------------------------------------
   public JPanel getPanel() {
      return this;
   }
}






