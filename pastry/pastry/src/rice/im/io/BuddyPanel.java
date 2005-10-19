

package rice.im.io;

import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.tree.*;

import java.io.*;
import java.util.*;

import rice.*;
import rice.im.*;

// FavoritesPanel extends JPanel
//   FavoritesTree extends JTree
//   |
//   +- FavoritesNode ext. DefaultMutableTreeNode, imp. FavoritesTreeNode
//      |
//      +- ServersFolderNode ext. DefaultMutableTreeNode, imp. FavoriteTreeNode
//      |  +- ServerNode
//      |  +- ServerNode
//      |  +- ...
//      |
//      +- ChannelsFolderNode
//      |  +- ChannelsNode
//      |  +- ChannelsNode
//      |  +- ...
//      |
//      +- UsersFolderNode
//         +- UsersNode
//         +- UsersNode
//         +- ...


public class BuddyPanel extends JPanel {

   private JTree   _tree;

   /** Row height of items in favorites tree. */
    public static final int ROW_HEIGHT = 20;

    //-----------------------------------------------------------------
    public BuddyPanel(IMGui app) {

	setLayout(new BorderLayout());
	setBorder(new BevelBorder(BevelBorder.LOWERED));
	
	_tree = new BuddyTree(this);
	_tree.setRowHeight(ROW_HEIGHT);
	add(new JScrollPane(_tree),BorderLayout.CENTER);
    }

    public JPanel getPanel() {return this;}


}

/** This interface is needed because we need to extend each node from DefaultMutableTreeNode and from BuddyTreeNode, but java doesn't allow multiple inheritance **/


interface BuddyTreeNode {
   public JPopupMenu createPopupMenu();
   public void handleDoubleClick();
   
}



/** Tree with root that is a FavoritesNode. */
 class BuddyTree extends JTree {

   private BuddyNode _buddyNode;
   private BuddyPanel _buddyPanel;

   public static final String SERVERS_FOLDER  = "Servers";
   public static final String CHANNELS_FOLDER = "Channels";
   public static final String USERS_FOLDER    = "Users";

   //-----------------------------------------------------------------
   /** Construct favorites tree for a favorites panel.  */
   public BuddyTree(BuddyPanel buddyPanel) {

      _buddyPanel = buddyPanel;
      _buddyNode = new BuddyNode();

      setCellRenderer(new BuddyTreeCellRenderer());
      setShowsRootHandles(true);
      putClientProperty("JTree.lineStyle", "Angled"); 

      DefaultTreeModel model = new DefaultTreeModel(_buddyNode);
      setModel(model);


      try {((DefaultTreeModel)getModel()).reload();} catch (Exception e) {}
      expandPath(new TreePath(_buddyNode.getFriendsNode().getPath()));
      expandPath(new TreePath(_buddyNode.getFamilyNode().getPath()));
      expandPath(new TreePath(_buddyNode.getWorkersNode().getPath()));
      


      // Listen for pop-up menu clicks
      addMouseListener(new MouseAdapter() {
	      public void mousePressed(MouseEvent me) { showPopup(me); }
	      public void mouseReleased(MouseEvent me) { showPopup(me); }
	      public void mouseClicked(MouseEvent me) {
		  
		  // If user double-clicked on a tree node then...
		  if (me.getClickCount() == 2) {
		      
		      DefaultMutableTreeNode treeNode = null;
		      Point pt = me.getPoint();
		      TreePath treePath = getPathForLocation(pt.x,pt.y);
		      if (treePath!=null) {
			  
			  int pathLength = treePath.getPath().length;
			  treeNode =
			      (DefaultMutableTreeNode)treePath.getPath()[pathLength-1];
			  setSelectionPath(treePath);
			  
			  if (treeNode instanceof BuddyTreeNode) {
			      BuddyTreeNode buddyTreeNode =
				  (BuddyTreeNode)treeNode;
			      
			      // ...ask tree node to handle it.
			      buddyTreeNode.handleDoubleClick();
			  }
		      }
		  }
		  else showPopup(me);
	      }
	  });
   }

   //-----------------------------------------------------------------
   public void showPopup(MouseEvent me) {

      if (me.isPopupTrigger()) {

         // If user right-clicked on a tree node...
         DefaultMutableTreeNode treeNode = null;
         Point pt = me.getPoint();
         TreePath treePath = getPathForLocation(pt.x,pt.y);
         if (treePath!=null) {

            int pathLength = treePath.getPath().length;
            treeNode = (DefaultMutableTreeNode)
               treePath.getPath()[pathLength-1];
            setSelectionPath(treePath);

            if (treeNode instanceof BuddyTreeNode) {

               BuddyTreeNode buddyTreeNode =
                  (BuddyTreeNode)treeNode;

               // ...then present tree node's popup menu
               JPopupMenu popup = buddyTreeNode.createPopupMenu();
               if (popup != null) {
                  add(popup);
                  popup.show(BuddyTree.this,me.getX(),me.getY());
               }
            }
         }
        
      }
   }
  
}

///////////////////////////////////////////////////////////////////////

/**
 * Tree node with three subnodes Servers, Users and Channels.
 */
class BuddyNode extends DefaultMutableTreeNode {

   private UserFolderNode  _bfriends;
   private UserFolderNode _bfamily;
    private UserFolderNode    _bworkers;

   public UserFolderNode  getFriendsNode()  {return _bfriends;}
   public UserFolderNode getFamilyNode() {return _bfamily;}
   public UserFolderNode    getWorkersNode()    {return _bworkers;}

   //-------------------------------------------------------------
   public BuddyNode() {
      super("Favorites");

      _bfriends = new UserFolderNode(Buddy.FRIEND); _bfriends.refresh(); add(_bfriends);

      _bfamily = new UserFolderNode(Buddy.FAMILY);  _bfamily.refresh(); add(_bfamily);

      _bworkers = new UserFolderNode(Buddy.WORKER); _bworkers.refresh(); add(_bworkers);

   }
}

///////////////////////////////////////////////////////////////////////

class UserFolderNode
    extends DefaultMutableTreeNode implements BuddyTreeNode {

    private String _name;
   //-----------------------------------------------------------------
   public UserFolderNode(String name) {
      super(name);
      setAllowsChildren(true);
      _name = name;
				
   }
   //-----------------------------------------------------------------
   public void refresh() {
      removeAllChildren();
      Vector vec = IMGui.getIMGui().getClient().getService().all_contacts;
      for (int i = 0; i < vec.size(); i++) {
	  Buddy bud = (Buddy) vec.get(i);
	  if (bud.getIndicator().equals(_name)) {
	      add(new UserNode(bud.getName()));
	  }
      }
   }

    public String  getName() {
	return _name;
    }
 
   //-----------------------------------------------------------------
   public JPopupMenu createPopupMenu() {

      JPopupMenu popup = new JPopupMenu();

      popup.add(IMGui.getIMGui().getAction(
         IMGui.ADD_CONTACT).getActionObject());

      popup.add(IMGui.getIMGui().getAction(
         IMGui.ADD_CONTACT).getActionObject());

      popup.add(IMGui.getIMGui().getAction(
         IMGui.ADD_CONTACT).getActionObject());

      return popup;
   }
   //-----------------------------------------------------------------
   public void handleDoubleClick() {
   }

   
}


///////////////////////////////////////////////////////////////////////

class UserNode
   extends DefaultMutableTreeNode implements BuddyTreeNode {

    private String _name;
    private UserNodeAction send_handler;
    private UserNodeAction remove_handler;
    private UserNodeAction view_handler;
    
    //-----------------------------------------------------------------
    public UserNode(String name) {
	super(name);
	_name = name;
	send_handler = new UserNodeAction(new ImageIcon(IMGui.getIMGui().prefix + "Plug.gif"),"Send Message", this);
	remove_handler = new UserNodeAction(new ImageIcon(IMGui.getIMGui().prefix + "Unplug.gif"),"Remove Contact", this);
	 view_handler = new UserNodeAction(new ImageIcon(IMGui.getIMGui().prefix  + "Plug.gif"),"View User Info", this);
    }
   //-----------------------------------------------------------------
   public JPopupMenu createPopupMenu() {
       JPopupMenu popup = new JPopupMenu();
       JMenuItem sm = new JMenuItem("Send Message"); sm.setAction(send_handler); popup.add(sm);
       JMenuItem rc = new JMenuItem("Remove Contact"); rc.setAction(remove_handler); popup.add(rc);
       JMenuItem vui = new JMenuItem("View User Info"); vui.setAction(view_handler); popup.add(vui);
       return popup;
       
   }
  
   //-----------------------------------------------------------------
     public void handleDoubleClick() {
      
     }
     
     public String getName() {
	 return _name;
     }

     

}


 class UserNodeAction extends ChatAction {
    
     private UserNode _node;
     private String _id;

     UserNodeAction(Icon icon, String id, UserNode node) {
	 super(id, icon);
	 _node = node;
	 _id = id;
	 
    }
    
    public void actionPerformed (ActionEvent e) {
	if (_id.equals("Send Message")) {
	    System.out.println("inside send message");
	    
	     JFrame fr = new JFrame("Send message to " + _node.getName());
	      JPanel pan = new JPanel(new BorderLayout());
	      Vector vec = new Vector();
	      vec.add(fr); vec.add("");
	      IMGui.getIMGui().open_conn.put(_node.getName(), vec);

	      ChatFrame  cf = new ChatFrame(new ChatPanel(IMGui.getIMGui(), _node));
	      pan.add(cf, BorderLayout.CENTER);
	      fr.getContentPane().add(cf);
	      fr.setSize(300, 300);
	      IMGui.getIMGui().centerOnScreen(fr);

	      
	      fr.setVisible(true);
	}
	
	else if (_id.equals("Remove Contact")) {
	    System.out.println("remove contact");
	    final Buddy bud = new Buddy(_node.getName(), ((UserFolderNode)_node.getParent()).getName());
	    final SwingWorker worker = new SwingWorker() {
			public Object construct() {
			    IMGui.getIMGui().getClient().getService().removeContact(bud, new Continuation() {
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
	    
     else if (_id.equals("View User Info")) {
	 System.out.println("View user info of " + _node.getName());
     }
     
    }
 }



///////////////////////////////////////////////////////////////////////

/** Tree cell renderer that knows how to draw servers, users and channels. */
class BuddyTreeCellRenderer extends DefaultTreeCellRenderer {

    private String prefix = "/home/anwisdas/pastry/src/rice/im/io/images/";
   //--------------------------------------------------------------------------
   public BuddyTreeCellRenderer() {
      setClosedIcon(new ImageIcon(prefix + "Closed.gif"));
      setOpenIcon(new ImageIcon(prefix + "Open.gif"));
      setLeafIcon(new ImageIcon(prefix + "Leaf.gif"));
   }
   //--------------------------------------------------------------------------
   public Component getTreeCellRendererComponent(JTree tree, Object value,
      boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
       
       Font newFont, oldFont;

       Component comp = super.getTreeCellRendererComponent(
          tree,value,sel,expanded,leaf,row,hasFocus);

      JLabel label = (JLabel)comp;
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;

      // By default, use plain font
      if (label.getFont() != null) {
          oldFont = label.getFont();
         label.setFont(
            new Font(oldFont.getName(),Font.PLAIN,oldFont.getSize()));
      }
      else 
	  oldFont = new Font("Arial", Font.PLAIN, 12);
      
      Vector vec = IMGui.getIMGui().getClient().getService().online_contacts;
      
   
      
      String nm = (String) node.getUserObject().toString();
      if ( vec.contains(nm) )
	  newFont = new Font(oldFont.getName(),Font.BOLD,oldFont.getSize());
      else
	  newFont = new Font(oldFont.getName(),Font.PLAIN,oldFont.getSize());
      setFont(newFont);

      
      return comp;
   }
}














