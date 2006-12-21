/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/
package rice.email.proxy.mailbox.postbox;

import rice.*;
import rice.Continuation.*;
import rice.email.*;
import rice.post.*;

import rice.email.proxy.mail.*;

import rice.email.proxy.mailbox.*;

import rice.email.proxy.util.*;

import java.io.*;

import java.util.*;
import java.util.regex.Pattern;

/**
 * This class translates between foedus and the
 * emailservice.
 */
public class PostFolder implements MailFolder {

  // the POST representation of this folder
  private Folder folder;
  
  // the parent of this folder
  private PostFolder parent;

  // the local email service
  private EmailService email;

  // a cache of PostMessages
  private HashMap messageCache;
  
  // a cache of the previously-fetch folders
  private Hashtable folders;

  /**
   * Builds a folder given a string name
   */
  public PostFolder(Folder folder, PostFolder parent, EmailService email) throws MailboxException {
    
    if (email == null)
      throw new MailboxException("EmailService cannot be null.");

    if (folder == null)
      throw new MailboxException("Post folder cannot be null.");

    this.folder = folder;
    this.parent = parent;
    this.email = email;
    this.messageCache = new HashMap();
    this.folders = new Hashtable();
  }

  /**
   * Returns the full name of this folder.
   *
   * @return The name of this folder.
   */
  public String getFullName() {
    if (! parent.getFolder().isRoot())
      return parent.getFullName() + PostMailbox.HIERARCHY_DELIMITER + folder.getName();
    else
      return folder.getName();
  }

  public int getNextUID() throws MailboxException {
    return folder.getNextUID();
  }

  public int getExists() throws MailboxException {
    return getMessages(MsgFilter.ALL).size();
  }

  public int getRecent() throws MailboxException {
    return getMessages(MsgFilter.RECENT).size();
  }

  public Folder getFolder() {
    return folder;
  }

  public PostFolder getParent() {
    return parent;
  }
  
  public void setParent(PostFolder parent) {
    this.parent = parent;
  }
  
  public void delete() throws MailboxException {
    delete(true);
  }

  public void delete(boolean checkForInferiors) throws MailboxException {
    if (checkForInferiors)
      if (getChildren().length > 0)
        throw new MailboxException("Folder contains child folders, unable to delete.");
    
    parent.folders.remove(folder.getName());
    try {
      new ExternalContinuationRunnable() {
        protected void execute(Continuation c) {
          parent.getFolder().removeFolder(folder.getName(), c);
        }
      }.invoke(parent.getFolder().getPost().getEnvironment());
    } catch (Exception e) {
      throw new MailboxException(e);
    }
  }

  public void put(MovingMessage msg) throws MailboxException {
    put(msg, new LinkedList(), folder.getPost().getEnvironment().getTimeSource().currentTimeMillis());
  }

  public void put(MovingMessage msg, List lflags, final long internaldate) throws MailboxException {
    try {
      Email anEmail = msg.getEmail();

      if (anEmail == null) {
        anEmail = PostMessage.parseEmail(this.email.getLocalHost(), null, msg.getResource(), folder.getPost().getEnvironment());
      }
      
      final Email email = anEmail;
      final Flags flags = new Flags();

      Iterator i = lflags.iterator();

      while (i.hasNext()) {
        flags.setFlag((String) i.next(), true);
      }

      try {
        new ExternalContinuationRunnable() {
          protected void execute(Continuation c) {
              folder.addMessage(email, flags, internaldate, c);
          }
        }.invoke(folder.getPost().getEnvironment());
      } catch (Exception e) {
        throw new MailboxException(e);
      }
      
      List all = getMessages(MsgFilter.ALL);
      ((PostMessage) all.get(all.size()-1)).getFlagList().setRecent(true);
    } catch (IOException e) {
      throw new MailboxException(e);
    }
  }
 
  public List getMessages(MsgFilter range) throws MailboxException {
    StoredEmail[] emails; 
    try {
      emails = (StoredEmail[])(new ExternalContinuationRunnable() {
        protected void execute(Continuation c) {
          folder.getMessages(c);
        }
      }).invoke(folder.getPost().getEnvironment());
    } catch (Exception e) {
      throw new MailboxException(e);
    }
    
    LinkedList list = new LinkedList();

    for (int i=0; i<emails.length; i++) {
      PostMessage msg = null;

      if (messageCache.get(emails[i]) == null) {
        msg = new PostMessage(emails[i], i+1, this.getFolder());
        messageCache.put(emails[i], msg);
      } else {
        msg = (PostMessage) messageCache.get(emails[i]);
        msg.setSequenceNumber(i+1);
      }

      if (range.includes(msg)) {
        list.addLast(msg);
      }
    }

    return list;
  }
  
  public void copy(MovingMessage[] messages, List[] flags, final long[] internaldates) throws MailboxException {
    if (messages.length == 0)
      return;
    
    final Email[] emails = new Email[flags.length];
    final Flags[] realFlags = new Flags[flags.length];
    
    for (int i=0; i<realFlags.length; i++) {
      emails[i] = messages[i].getEmail();
      Iterator j = flags[i].iterator();
      realFlags[i] = new Flags();
    
      while (j.hasNext()) 
        realFlags[i].setFlag((String) j.next(), true);
      
      if (internaldates[i] == 0)
        internaldates[i] = folder.getPost().getEnvironment().getTimeSource().currentTimeMillis();
    }
    
    try {
      new ExternalContinuationRunnable() {
        protected void execute(Continuation c) {
          folder.addMessages(emails, realFlags, internaldates, c);
        }
      }.invoke(folder.getPost().getEnvironment());
    } catch (Exception e) {
      throw new MailboxException(e);
    }
      
    List all = getMessages(MsgFilter.ALL);
    
    for (int i=0; i<flags.length; i++)
      ((PostMessage) all.get(all.size()-i-1)).getFlagList().setRecent(true);
  }
  
  public void update(StoredMessage[] messages) throws MailboxException {
    if (messages.length == 0)
      return;
    
    final StoredEmail[] emails = new StoredEmail[messages.length];
    
    for (int i=0; i<messages.length; i++)
      emails[i] = ((PostMessage) messages[i]).getStoredEmail();
    
    try {
      new ExternalContinuationRunnable() {
        protected void execute(Continuation c) {
          folder.updateMessages(emails, c);
        }
      }.invoke(folder.getPost().getEnvironment());
    } catch (Exception e) {
      throw new MailboxException(e);
    }
  }
  
  public void purge(StoredMessage[] messages) throws MailboxException {
    if (messages.length == 0)
      return;
    
    final StoredEmail[] emails = new StoredEmail[messages.length];
    
    for (int i=0; i<messages.length; i++)
      emails[i] = ((PostMessage) messages[i]).getStoredEmail();
    
    try {
      new ExternalContinuationRunnable() {
        protected void execute(Continuation c) {
          folder.removeMessages(emails, c);
        }
      }.invoke(folder.getPost().getEnvironment());
    } catch (Exception e) {
      throw new MailboxException(e);
    }
  }

  public String getUIDValidity() throws MailboxException {
    return folder.getCreationTime() + "";
  }
  
  public MailFolder createChild(final String name) throws MailboxException {
    Folder f;
    try {
      f = (Folder)(new ExternalContinuationRunnable() {
        protected void execute(Continuation c) {
          folder.createChildFolder(name, c);
        }
      }).invoke(folder.getPost().getEnvironment());
    } catch (Exception e) {
      throw new MailboxException(e);
    }
    
    folders.put(name, new PostFolder(f, this, email));
    
    return getChild(name);
  }
  
  public MailFolder getChild(final String name) throws MailboxException {
    String[] names = folder.getChildren();
    Arrays.sort(names);
    
    if (Arrays.binarySearch(names, name) < 0) {
      throw new MailboxException("Folder " + name + " does not exist!");
    }
    
    if (folders.get(name) != null) {
      return (MailFolder) folders.get(name);
    }
    
    Folder f;
    try {
      f = (Folder)(new ExternalContinuationRunnable() {
        protected void execute(Continuation c) {
          folder.getChildFolder(name, c);
        }
      }).invoke(folder.getPost().getEnvironment());
    } catch (Exception e) {
      throw new MailboxException(e);
    }
    
    folders.put(name, new PostFolder(f, this, email));
    
    return (MailFolder) folders.get(name);
  }
  
  public MailFolder[] getChildren() throws MailboxException {
    String[] names = folder.getChildren();
    MailFolder[] result = new MailFolder[names.length];
    Arrays.sort(names);    
    
    for (int i=0; i<result.length; i++) 
      result[i] = getChild(names[i]);
    
    return result;
  }
}









