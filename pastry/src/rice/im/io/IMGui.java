
package rice.im.io;

//import org.relayirc.chatengine.*;
//import org.relayirc.swingutil.*;
//import org.relayirc.util.Debug;

//import org.python.util.PythonInterpreter;
//import org.python.core.*;

//import com.l2fprod.gui.plaf.skin.*;

import javax.swing.*;
import javax.swing.text.*;
import javax.swing.border.*;

import java.lang.reflect.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.beans.*;
import java.awt.*;
import java.awt.event.*;

import rice.*;
import rice.im.*;


public class IMGui extends JFrame {

    private static final String  _appname = "IMPost";
    private static final String  _appversion = "Version 1.0.0";
    
    private static IMGui _imGui = null;
    
    private JPanel _panel;
    private BuddyFrame _buddyframe;
    private ChatFrame _chatframe;
    
    
    private _MenuBar       _menuBar;
    private _ToolBar       _toolBar;
   
    private boolean        _statusBarEnabled = true;
 
    IMClient _client;
    private static _StyleContext _styles;

    public Hashtable open_conn = new Hashtable();

   /** MDIFrames keyed by MDIPanels. */
   private Hashtable _framesByPanel = new Hashtable();

   /**
    * User queue. Collection of user objects each waiting to be
    * populated with data from an incoming WHOIS reply from the server.
    * The hashtable is keyed user objects and the values are Booleans.
    * If a user's boolean is true, then IMGui will popup a dialog to
    * show the WHOIS information for the user as soon as the reply comes
    * in. Otherwise, no dialog will be shown. */
   private Hashtable _userQueueHash = new Hashtable();

   /** Action collection. Hashtable of IChatAction values keyed by
    * action name. */
   private Hashtable _actions = null;

   /**
   * Action initialization array. The items in this array will be
   * used to initialized the _actions hashtable on the first time that
   * getAction(String name) is called.<p/>
   * To add a new action:<ul>
   * <li>Create an action class that implements IChatAction
   * <li>Pick an icon for the action in the images directory
   * <li>Add a public static final String action name for it
   * <li>Add action class, icon name and command name to this array
   * <li>Now your action is available via the getAction method</ul>
   */

    public String prefix = "/home/anwisdas/pastry/src/rice/im/io/images/";
    private Object[][] _actionArray = {
      //
      // Action Class            Icon Name         Command Name
      // ------------            ---------         ------------
      { new _SendMessage(new ImageIcon(prefix + "Inform.gif")),      SEND_MESSAGE },
      { new _SendMessageIcon (new ImageIcon(prefix + "TileCascade.gif")),   SEND_MESSAGE_ICON },
      { new _AddContact( new ImageIcon(prefix + "Plug.gif")), ADD_CONTACT          },
      { new _RemoveContact(new ImageIcon(prefix + "Hammer.gif")),        REMOVE_CONTACT},
      { new _RemoveContactIcon (new ImageIcon(prefix + "Hammer.gif")),     REMOVE_CONTACT_ICON},
      { new _ViewUserInfo (new ImageIcon(prefix + "UnPlug.gif")),          VIEW_USER_INFO },
      { new _ExitAction (new ImageIcon(prefix + "ReplyAll.gif")),       EXIT },
      { new _AboutAction (new ImageIcon(prefix + "Inform.gif")), ABOUT }, 

     };

   // ------------
   // Action names
   // ------------
   // Names for the getAction(String name) method.
   //
   /** Action name for the ABOUT action. */
   public static final String SEND_MESSAGE = "Send_Message";

   /** Action name for the CASCADE action. */
   public static final String SEND_MESSAGE_ICON = "Send_Message_Icon";

   /**
    * Action name for CONNECT action. You may supply a server
    * value by setting the action's "Server" property to the Server
    * object that you wish to connect. If this value is not set,
    * then the ConnectDlg dialog will be shown so that the user may
    * choose a server. */
   public static final String ADD_CONTACT = "Add_Contact";

   /** Action name for the CUSTOMIZER_ACTIONS action. */
   public static final String REMOVE_CONTACT = "Remove_Contact";

   /** Action name for the CUSTOMIZE_LISTENERS action. */
   public static final String REMOVE_CONTACT_ICON = "Remove_Contact_Icon";

   /** Action name for the DISCONNECT action. This action will
    * trigger a disconnect from the current server. */
   public static final String VIEW_USER_INFO = "View_User_Info";

    /** Action name for the EXIT action. */
    public static final String EXIT = "Exit";
    
    public static final String ABOUT = "About";
    
   //------------------------------------------------------------------
   /** Main method for the Relay-JFC chat application. */

   public static void main( String[] args ) {
       
       if (args.length>0 && args[0].toString().equals("-d")) {
         Debug.setDebug(true);
      }
       System.out.println("Starting up...");
      _imGui = new IMGui(null);
      _imGui.run();

   }

   
	
   //------------------------------------------------------------------
   /** Returns the one-and-only chat application object. */
   public static IMGui getIMGui() {
      return _imGui;
   }
   //------------------------------------------------------------------
   /** Returns the one-and-only chat application object. */
   public static void setIMGui(IMGui a) {
      _imGui = a;
   }
   //------------------------------------------------------------------
   /** Construct a chat application. */

   public IMGui(IMClient client) {
      super(_appname + " " +  _appversion);
      _client = client;
      //_python.exec("zzz=1"); // warm-up the interpreter...
   }

    public IMClient getClient() {
	return _client;
    }
   
   //------------------------------------------------------------------
   /** The main application thread, for internal use only. */

   public void run() {


      // Create, init and layout GUI
      initGUI();
      layoutGUI();

      

      // Set initial size and center on screen.
      setSize(800,600);
      Dimension ssize = Toolkit.getDefaultToolkit().getScreenSize();
      setLocation( (ssize.width/2)-(getSize().width/2),
                   (ssize.height/2)-(getSize().height/2));

      // Go live
      setVisible(true);
      toFront();
   }
   //------------------------------------------------------------------
   /**
    * Get action by name. See action name fields IMGui.CONNECT
    * IMGui.DISCONNECT, etc. for possible values.
    */
   public IChatAction getAction(String actionName) {
      if (_actions == null) {
         _actions = initActions(_actionArray,this);
      }
      return (IChatAction)_actions.get(actionName);
   }
   //------------------------------------------------------------------
    public  Hashtable initActions(Object[][] a, Object b) {
	Hashtable actions = new Hashtable();
	for (int i = 0; i < a.length; i++) {
	    String name = (String) a[i][1];
	    IChatAction ica = (IChatAction) a[i][0];
	    actions.put(name, ica);
	}
	
	return actions;
    }

   //==================================================================
   // Accessors
   //==================================================================

   /** Returns the name of the chat application. */
   public String getAppName() {
      return _appname;
   }
   //------------------------------------------------------------------
   /** Returns the version of the chat application. */
   public String getAppVersion() {
      return _appversion;
   }
   //------------------------------------------------------------------
   public static Style getChatStyle(String st) {
      return _styles.getStyle(st);
   }
   //------------------------------------------------------------------
   public static void setChatFont(Font font) {
      _styles.setFont(font);
   }
   //------------------------------------------------------------------
  
   
   
   /** Disconnect from the IRC server, save options and exit applicatin. */
   public void closeApp() {
       System.exit(0);
   }
   

   //==================================================================
   // Implementation
   //==================================================================

   public void initGUI() {

       //updateLookAndFeel();

      getContentPane().setLayout(new BorderLayout());
      _panel = new JPanel();
      _panel.setLayout(new BorderLayout());
      //_chatframe = new ChatFrame(new ChatPanel(this));
      
      
      // the File Command ... Help menu bar at the top
      setJMenuBar(_menuBar = new _MenuBar());

      

      // Add listener to quit on frame close
      addWindowListener( new WindowAdapter() {
         public void windowClosing(WindowEvent e) {
            closeApp();
         }
      });
   }
   //------------------------------------------------------------------

   public void layoutGUI() {

      getContentPane().removeAll();

      // the 8 icons at the top
      getContentPane().add(_toolBar = new _ToolBar(),BorderLayout.NORTH);
      
      // the time at the bottom right
      //getContentPane().add(_statusBar,BorderLayout.SOUTH);
      
      // the purple panel in the middle
      getContentPane().add(_panel,BorderLayout.CENTER);
      
      
      _panel.setBackground(new Color((float)0,(float) 0.75,(float) 0.75));
      
      refreshBuddyFrame();
     
      //_panel.add(_chatframe, BorderLayout.SOUTH);
      
      _panel.setVisible(true);

      
   }
   
    public void refreshBuddyFrame() {
	_buddyframe = new BuddyFrame(this);
	_panel.add(_buddyframe, BorderLayout.EAST);
    }


    public void centerOnScreen(Component com) {
	Dimension ssize = Toolkit.getDefaultToolkit().getScreenSize();
	com.setLocation(
			(ssize.width/2) - (com.getSize().width/2),
			(ssize.height/2) - (com.getSize().height/2) );
    }
  
   private class _MenuBar extends JMenuBar {

      private JMenu _commandsMenu = new JMenu("Commands",false);

      private JCheckBoxMenuItem _consoleItem;
      private JCheckBoxMenuItem _favoritesItem;
      private JCheckBoxMenuItem _pythonItem;

      public _MenuBar() {

         // FILE MENU
         JMenu fileMenu = new JMenu("File",false);
         this.add(fileMenu);

         fileMenu.add(getAction(
            ADD_CONTACT ).getActionObject()).setAccelerator(
         KeyStroke.getKeyStroke(KeyEvent.VK_S,Event.CTRL_MASK));

         fileMenu.add(getAction(
            ADD_CONTACT ).getActionObject()).setAccelerator(
         KeyStroke.getKeyStroke(KeyEvent.VK_A,Event.CTRL_MASK));

         fileMenu.addSeparator();

         fileMenu.add(getAction(
            ADD_CONTACT ).getActionObject()).setAccelerator(
         KeyStroke.getKeyStroke(KeyEvent.VK_C,Event.CTRL_MASK));

         fileMenu.add(getAction(
            ADD_CONTACT ).getActionObject()).setAccelerator(
         KeyStroke.getKeyStroke(KeyEvent.VK_D,Event.CTRL_MASK));

         fileMenu.addSeparator();

         fileMenu.add(getAction(
            EXIT ).getActionObject()).setAccelerator(
         KeyStroke.getKeyStroke(KeyEvent.VK_X,Event.CTRL_MASK));

         // COMMANDS MENU
         this.add(_commandsMenu);
         reloadCommandsMenu();

         

         // HELP MENU
         JMenu helpMenu = new JMenu("Help",false);
         this.add(helpMenu);

         helpMenu.add(getAction(
            ABOUT ).getActionObject()).setAccelerator(
         KeyStroke.getKeyStroke(KeyEvent.VK_H,Event.CTRL_MASK));
      }
      //-----------------------------------------------------
      public void reloadCommandsMenu() {

         _commandsMenu.removeAll();

         _commandsMenu.add(getAction(
            ADD_CONTACT ).getActionObject()).setAccelerator(
         KeyStroke.getKeyStroke(KeyEvent.VK_J,Event.CTRL_MASK));

         _commandsMenu.add(getAction(
            SEND_MESSAGE_ICON ).getActionObject()).setAccelerator(
         KeyStroke.getKeyStroke(KeyEvent.VK_W,Event.CTRL_MASK));

         _commandsMenu.addSeparator();

         // Add custom menu items

         
         

      }
      //-----------------------------------------------------
      public JCheckBoxMenuItem getPythonMenuItem() {
         return _pythonItem;
      }
      //-----------------------------------------------------
      public JCheckBoxMenuItem getConsoleMenuItem() {
         return _consoleItem;
      }
      //-----------------------------------------------------
      public JCheckBoxMenuItem getFavoritesMenuItem() {
         return _favoritesItem;
      }
   }

   ////////////////////////////////////////////////////////////////////////

   private class _ToolBar extends JToolBar {

      public _ToolBar() {

         setFloatable(false);
         this.setMargin(new Insets(0,0,0,0));

         createButton(getAction(ADD_CONTACT).getActionObject()).setMargin(
            new Insets(5,5,5,5));

         addSeparator();
         createButton(getAction( REMOVE_CONTACT_ICON).getActionObject()).setMargin(
            new Insets(5,5,5,5));
         createButton(getAction( SEND_MESSAGE_ICON).getActionObject()).setMargin(
            new Insets(5,5,5,5));

         addSeparator();
         createButton(getAction( ABOUT).getActionObject()).setMargin(
            new Insets(5,5,5,5));
         createButton(getAction( ADD_CONTACT ).getActionObject()).setMargin(
            new Insets(5,5,5,5));

         addSeparator();
         createButton(getAction( ADD_CONTACT).getActionObject()).setMargin(
            new Insets(5,5,5,5));
         createButton(getAction( ADD_CONTACT).getActionObject()).setMargin(
            new Insets(5,5,5,5));
         createButton(getAction( ADD_CONTACT).getActionObject()).setMargin(
            new Insets(5,5,5,5));
      }
      //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
      private JButton createButton( Action action ) {
         JButton button = this.add(action);
         String tip = (String)action.getValue(Action.SHORT_DESCRIPTION);
         button.setMargin(new Insets(1,1,1,1));
         button.setToolTipText(tip);
         button.setText("");
         return button;
      }
   }
   ////////////////////////////////////////////////////////////////////////
   

    //////////////////////////////////////////////////////////GUI HELPER METHODS////////////////////////////////////////////////////////////////////

    public void getBuddyFromGui(final AbstractAction act) {
	
	
	
	final JFrame fr = new JFrame("Getting contact....");
	
	JPanel rightpanel = new JPanel(new BorderLayout());
	
	JRadioButton friend_button = new JRadioButton("Friends"); friend_button.setSelected(true);
	JRadioButton family_button = new JRadioButton("Family");
	JRadioButton worker_button = new JRadioButton("Workers");
	
	ButtonGroup bg = new ButtonGroup(); 
	bg.add(friend_button);
	bg.add(family_button);
	bg.add(worker_button);
	
	final ButtonInputListener bil = new ButtonInputListener();
	friend_button.addActionListener(bil);
	family_button.addActionListener(bil);
	worker_button.addActionListener(bil);
	
	rightpanel.add(friend_button, BorderLayout.NORTH);
	rightpanel.add(family_button, BorderLayout.CENTER);
	rightpanel.add(worker_button, BorderLayout.SOUTH);

	fr.getContentPane().add(rightpanel, BorderLayout.EAST);

	JPanel leftpanel = new JPanel(new BorderLayout());
	leftpanel.add(new JLabel("Please Enter Contact Name"), BorderLayout.NORTH);
	final JTextField txt = new JTextField();
	leftpanel.add(txt, BorderLayout.CENTER);
	JButton ok_button = new JButton("OK");
	ok_button.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent e) {
		    System.out.println("inside actionperformed for ok_button");
		    Buddy bud;
		    if (bil.current_choice.equals("Friends")) 
			bud =  new Buddy(txt.getText().trim(), Buddy.FRIEND);
		    else if (bil.current_choice.equals("Family"))
			bud =  new Buddy(txt.getText(), Buddy.FAMILY);
		    else if (bil.current_choice.equals("Workers"))
			bud =  new Buddy(txt.getText(), Buddy.WORKER);
		    else bud =  new Buddy("error", Buddy.FRIEND);       // NOTE: FOR DEBUGGING PURPOSES ONLY!!!
		    fr.setVisible(false);
		    if (act instanceof _AddContact) {
			((_AddContact)act).doWork(bud);
		    }
		    else 
			((_RemoveContactIcon)act).doWork(bud);
		}
	    }
		
		);
	leftpanel.add(ok_button, BorderLayout.SOUTH);
	fr.getContentPane().add(leftpanel, BorderLayout.CENTER);
	fr.setSize(350, 100);
	centerOnScreen(fr);
	fr.setVisible(true);
    }


    public void messageDisplay(String sender, String message) {
	
	JFrame fr;
	ChatPanel cpan;
	JPanel pan = new JPanel(new BorderLayout());
	String total = "";
	Vector vec;

	System.out.println("inside message display");
	if (open_conn.get(sender) == null) {
	    fr = new JFrame("Send message to " + sender);
	    vec = new Vector();
	    vec.add(fr); vec.add(new String(message));
	    open_conn.put(sender, vec);
	}
	else {
	    
	    vec = (Vector) open_conn.get(sender);
	    fr = (JFrame) vec.get(0);
	    total = (String) vec.get(1);
	}

	
	vec.set(1, total + "\n" + sender + ": " + message);
	open_conn.put(sender, vec);
	ChatFrame  cf = new ChatFrame(new ChatPanel(IMGui.getIMGui(), new UserNode(sender)));
	pan.add(cf, BorderLayout.CENTER);
	fr.getContentPane().removeAll();
	fr.getContentPane().add(cf);
	fr.setSize(300, 300);
	centerOnScreen(fr);
	fr.setVisible(true);
	
    }

    private class ButtonInputListener extends AbstractAction {
	String current_choice = "Friends";
	public ButtonInputListener() {
	    super();
	}

	public void actionPerformed(ActionEvent e) {
	    current_choice = e.getActionCommand();
	}
    }

   

    private class _SendMessage extends ChatAction {

      public _SendMessage(Icon icon) {

	  super("Send Message",icon);
      }
      public void actionPerformed(ActionEvent e) {
	 
	 
	  
	  
      }
   }
   ////////////////////////////////////////////////////////////////////////
   private class _SendMessageIcon extends ChatAction {

      public _SendMessageIcon(Icon icon) {
	  
	  super("Send Message",icon);
      }
      public void actionPerformed(ActionEvent e) {

	  String contact_name = JOptionPane.showInputDialog(IMGui.getIMGui(), "Please enter the name of the person you want to send the message to");
	  if (contact_name != null) {
	      JFrame fr = new JFrame("Send message to " + contact_name);
	      JPanel pan = new JPanel(new BorderLayout());
	      ChatFrame  cf = new ChatFrame(new ChatPanel(IMGui.getIMGui(), new UserNode(contact_name)) );
	      pan.add(cf, BorderLayout.CENTER);
	      fr.getContentPane().add(cf);
	      fr.setSize(300, 300);
	      centerOnScreen(fr);
	      fr.setVisible(true);
	  }
	      
      }
   }
   ////////////////////////////////////////////////////////////////////////
   private class _AddContact extends ChatAction {

      public _AddContact(Icon icon) {
         super("AddContact",icon);
      }
      public void actionPerformed(ActionEvent e) {
	  getBuddyFromGui(this);
      }

       public void doWork(final Buddy bud) {

	   if (bud != null) {
	       final SwingWorker worker = new SwingWorker() {
		       public Object construct() {
			   
			   _client.getService().addContact(bud, new Continuation() {
				   
				   public void receiveResult(Object o) {
				       finished();
				   }
				   
				   public void receiveException(Exception e) {
				       e.printStackTrace();
				   }
			       });
			   return new Boolean(true);
		       }
		       
		       public void finished() {
			   
			   IMGui.getIMGui().initGUI();
			   IMGui.getIMGui().layoutGUI();
			  
		       }
		   };
	       worker.start();
	   }
	   
       }

      public void update() {
         
      }
   }
   ////////////////////////////////////////////////////////////////////////
    private class _RemoveContactIcon extends ChatAction {
	
    public _RemoveContactIcon(Icon icon) {
         super("RemoveContact",icon);
      }
      public void actionPerformed(ActionEvent e) {
	  getBuddyFromGui(this);
      }

	public void doWork(final Buddy bud) {
	    
	    if (bud != null) {
		final SwingWorker worker = new SwingWorker() {
			public Object construct() {
			    _client.getService().removeContact(bud, new Continuation() {
				    public void receiveResult(Object o) {
					finished();
				    }
				    
				    public void receiveException(Exception e) {
					e.printStackTrace();
				    }
				});
			    return new Boolean(true);
			}
			
			public void finished() {
			    IMGui.getIMGui().initGUI();
			    IMGui.getIMGui().layoutGUI();
			    
			}
		    };
	    }
	    
	}
	
	public void update() {
	    
	}
    }
   ////////////////////////////////////////////////////////////////////////
   private class _RemoveContact extends ChatAction {

      public _RemoveContact(Icon icon) {
         super("Remove Contact",icon);
      }
      public void actionPerformed(ActionEvent e) {
	  String contact_name = JOptionPane.showInputDialog(IMGui.getIMGui(), "Please enter contact name to be removed");
	  System.out.println("Contact to be removed " + contact_name);
	  
	  
      }
      public void update() {
         
      }
   }
   ////////////////////////////////////////////////////////////////////////
   private class _ViewUserInfo extends ChatAction {

      public _ViewUserInfo(Icon icon) {
         super("View User Info",icon);
      }
      public void actionPerformed(ActionEvent e) {
	  System.out.println("View User Info button pressed");
      }
      public void update() {
         
      }
   }
   ////////////////////////////////////////////////////////////////////////
   private class _ExitAction extends ChatAction {
      public _ExitAction(Icon icon) {
         super("Exit",icon);
      }
      public void actionPerformed(ActionEvent e) {
         closeApp();
      }
   }

    private class _AboutAction extends ChatAction {
	public _AboutAction (Icon icon) {
	    super ("About", icon);
	}

	public void actionPerformed (ActionEvent e) {
	    JFrame fr = new JFrame("About");
	    JPanel panel = new JPanel( new BorderLayout());
	    String txt = "Copyright funkycolmadina";
	    JTabbedPane tp = new JTabbedPane(); 
	    tp.add("About", new JLabel(txt));
	    panel.add(tp, BorderLayout.CENTER);
	    fr.getContentPane().add(panel);
	    fr.setSize(200, 200);
	    centerOnScreen(fr);
	    fr.setVisible(true);
	    
	    
	}

    }
   
   ////////////////////////////////////////////////////////////////////////
   private class _StyleContext extends StyleContext {
      private Hashtable _colors = new Hashtable();

      public _StyleContext(Font font) {
         super();

         _colors.put("Black",Color.black);
         _colors.put("Blue",Color.blue);
         _colors.put("Cyan",Color.cyan);
         _colors.put("DarkGray",Color.darkGray);
         _colors.put("Gray",Color.gray);
         _colors.put("Green",Color.green);
         _colors.put("LightGray",Color.lightGray);
         _colors.put("Magenta",Color.magenta);
         _colors.put("Orange",Color.orange);
         _colors.put("Pink",Color.pink);
         _colors.put("Red",Color.red);
         _colors.put("White",Color.white);
         _colors.put("Yellow",Color.yellow);

         Style def = this.getStyle(StyleContext.DEFAULT_STYLE);

         for (Enumeration e = _colors.keys() ; e.hasMoreElements() ;) {

            // One style for normal weight font
            String key = (String)e.nextElement();
            Color col = (Color)_colors.get(key);
            Style s = addStyle(key,def);
            StyleConstants.setForeground(s,col);
            StyleConstants.setFontFamily(s,font.getName());
            StyleConstants.setFontSize(s,font.getSize());
            StyleConstants.setItalic(s,font.isItalic());
            StyleConstants.setBold(s,font.isBold());

            // One style for bold font
            s = addStyle("Bold-"+key,def);
            StyleConstants.setForeground(s,col);
            StyleConstants.setFontFamily(s,font.getName());
            StyleConstants.setFontSize(s,font.getSize());
            StyleConstants.setItalic(s,font.isItalic());
            StyleConstants.setBold(s,true);
         }
      }
      public void setFont(Font font) {
         for (Enumeration e = _colors.keys() ; e.hasMoreElements() ;) {
            Style s = this.getStyle((String)e.nextElement());
            StyleConstants.setFontFamily(s,font.getName());
            StyleConstants.setFontSize(s,font.getSize());
            StyleConstants.setItalic(s,font.isItalic());
            StyleConstants.setBold(s,font.isBold());
         }
      }
   }
}

/*
================================================================================
01234567890123456789012345678901234567890123456789012345678901234567890123456789
--------------------------------------------------------------------------------
0         1         2         3         4         5         6         7
////////////////////////////////////////////////////////////////////////////////
*/




