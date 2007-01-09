/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
package rice.email.proxy.imap;

import java.util.*;

import rice.email.proxy.mail.MovingMessage;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.user.*;
import rice.email.proxy.util.Workspace;
import rice.environment.Environment;
import rice.environment.logging.Logger;

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
  Environment environment;
  Logger logger;
  
  public ImapState(UserManager man, Workspace workspace, Environment env) {
    environment = env;
    _manager = man;
    _workspace = workspace;
    _queue = new Vector();
    logger = environment.getLogManager().getLogger(ImapState.class, null);
  }
  
  public Environment getEnvironment() {
    return environment; 
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
      if (logger.level <= Logger.WARNING) logger.logException("ERROR: Exception " + e + " thrown while printing unsolicited data.", e);
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
      if (logger.level <= Logger.SEVERE) logger.log(
          "ERROR: ImapState folder maintenance is wrong!");
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
