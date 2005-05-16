package rice.email.proxy.imap;

import java.util.*;

import rice.email.proxy.mail.MovingMessage;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.user.*;
import rice.email.proxy.util.Workspace;

/**
 * Holds current session state.
 * 
 * <p>
 * TODO:
 * </p>
 * 
 * <p>
 * At the moment, the only state information is the current Mailbox
 * and MailFolder. Some of the methods (enterMailbox/Folder) are not
 * state related.  In other words, state management and mailbox
 * access are going to need some major changes to be clean and work
 * correctly.
 * </p>
 * 
 * <p>
 * I'm thinking that the MailboxManager should probably be accessed
 * directly by commands, so this gets cleared out to contain nothing
 * but actual state data.
 * </p>
 */
public class ImapState {
  static Hashtable folderMap = new Hashtable();
  
  UserManager _manager;
  User _user;
  Mailbox _box;
  MailFolder _folder;
  boolean _authenticated;
  boolean _selected;
  boolean _loggedOut;
  Workspace _workspace;
  Vector _queue;
  int _cachedExists = -1;
  int _cachedRecent = -1;
  
  public ImapState(UserManager man, Workspace workspace) {
    _manager = man;
    _workspace = workspace;
    _queue = new Vector();
  }
  
  public MovingMessage createMovingMessage() {
    return new MovingMessage(_workspace);
  }
  
  public User getUser(String username) throws UserException  {
    return _manager.getUser(username);
  }
  
  public User getUser() {
    return _user;
  }
  
  public String getPassword(String username) throws UserException {
    return _manager.getPassword(username);
  }
  
  public void setUser(User user) throws UserException {
    _box = user.getMailbox();
    _user = user;
    _authenticated = true;
  }
  
  public void enterFolder(String fold) throws MailboxException {
    unselect();
    
    _folder = _box.getFolder(fold);
    _selected = true;
    
    enterFolder(_folder);
  }
  
  public Mailbox getMailbox() {
    return _box;
  }
  
  public MailFolder getFolder(String fold) throws MailboxException {
    return _box.getFolder(fold);
  }
  
  public MailFolder getSelectedFolder() {
    return _folder;
  }
  
  public boolean isAuthenticated() {
    return _authenticated && !_loggedOut;
  }
  
  public boolean isSelected() {
    return _selected && !_loggedOut;
  }
  
  public void unselect() {
    if (_selected)
      leaveFolder(_folder);
    
    _selected = false;
    _folder = null;
    
    _cachedExists = -1;
    _cachedRecent = -1;
  }
  
  public void logout() {
    _loggedOut = true;
    cleanup();
  }
  
  public void addUnsolicited(String update) {
    _queue.add(update);
  }
  
  public void broadcastUnsolicited(String update) {
    ImapState[] states = getFolder(_folder);
    
    for (int i=0; i<states.length; i++) 
      if (states[i] != this)
        states[i].addUnsolicited(update);
  }
  
  public void printUnsolicited(ImapConnection conn) {
    try {
      if (_selected) {
        if ((_cachedExists != _folder.getExists()) || (_queue.size() > 0)) {
          _queue.add(_folder.getExists() + " EXISTS");
          _cachedExists = _folder.getExists();
        }
        
        if ((_cachedRecent != _folder.getRecent())  || (_queue.size() > 0)) {
          _queue.add(_folder.getRecent() + " RECENT");
          _cachedRecent = _folder.getRecent();
        }
      }
    } catch (MailboxException e) {
      System.err.println("ERROR: Exception " + e + " thrown while printing unsolicited data.");
    }
    
    String[] result = (String[]) _queue.toArray(new String[0]);
    _queue.removeAllElements();
    
    for (int i=0; i<result.length; i++)
      conn.println("* " + result[i]);
  }
  
  void cleanup() {
    if (_selected)
      leaveFolder(_folder);
    
    _selected = false;
  }
  
  void enterFolder(MailFolder folder) {
    Vector v = (Vector) folderMap.get(folder);
    
    if (v == null) {
      v = new Vector();
      folderMap.put(folder, v);
    }
    
    if (! v.contains(this))
      v.addElement(this);
  }
  
  void leaveFolder(MailFolder folder) {
    Vector v = (Vector) folderMap.get(folder);
    
    if (v == null) {
      System.out.println("ERROR: ImapState folder maintenance is wrong!");
      return;
    }
    
    v.remove(this);
    
    if (v.size() == 0)
      folderMap.remove(folder);
  }
  
  static ImapState[] getFolder(MailFolder folder) {
    Vector v = (Vector) folderMap.get(folder);
    
    if (v == null)
      return new ImapState[0];
    
    return (ImapState[]) v.toArray(new ImapState[0]);
  }
}