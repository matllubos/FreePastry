package rice.email.proxy.mailbox.postbox;

import rice.*;
import rice.Continuation.*;
import rice.email.*;
import rice.post.*;

import rice.email.proxy.mail.MovingMessage;

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
  private Folder parent;

  // the local email service
  private EmailService email;

  // a cache of PostMessages
  private HashMap messageCache;

  /**
   * Builds a folder given a string name
   */
  public PostFolder(Folder folder, Folder parent, EmailService email) throws MailboxException {
    
    if (email == null)
      throw new MailboxException("EmailService cannot be null.");

    if (folder == null)
      throw new MailboxException("Post folder cannot be null.");

    this.folder = folder;
    this.parent = parent;
    this.email = email;
    this.messageCache = new HashMap();
  }

  /**
   * Returns the full name of this folder.
   *
   * @return The name of this folder.
   */
  public String getFullName() {
    return folder.getName();
  }

  public int getNextUID() {
    return folder.getNextUID();
  }

  public int getExists() {
    return folder.getExists();
  }

  public int getRecent() {
    return folder.getExists();
  }

  public Folder getFolder() {
    return folder;
  }

  public Folder getParent() {
    return parent;
  }

  public void delete() throws MailboxException {
    ExternalContinuation c = new ExternalContinuation();
    parent.removeFolder(getFullName(), c);
    c.sleep();

    if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }
  }

  public void put(MovingMessage msg) throws MailboxException {
    put(msg, new LinkedList(), null);
  }

  public void put(MovingMessage msg, List lflags, String date) throws MailboxException {
    try {
      Email email = msg.getEmail();

      if (email == null) {
        email = PostMessage.parseEmail(msg.getResource());
      }
      
      Flags flags = new Flags();

      Iterator i = lflags.iterator();

      while (i.hasNext()) {
        String flag = (String) i.next();

        if ("\\Deleted".equalsIgnoreCase(flag))
          flags.setDeleted(true);
        if ("\\Answered".equalsIgnoreCase(flag))
          flags.setAnswered(true);
        if ("\\Seen".equalsIgnoreCase(flag))
          flags.setSeen(true);
        if ("\\Flagged".equalsIgnoreCase(flag))
          flags.setFlagged(true);
        if ("\\Draft".equalsIgnoreCase(flag))
          flags.setDraft(true);
      }

      ExternalContinuation c = new ExternalContinuation();
      folder.addMessage(email, flags, c);
      c.sleep();

      if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }
    } catch (IOException e) {
      throw new MailboxException(e);
    }
  }
 
  public List getMessages(MsgFilter range) throws MailboxException {
    ExternalContinuation c = new ExternalContinuation();
    folder.getMessages(c);
    c.sleep();

    if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

    LinkedList list = new LinkedList();
    StoredEmail[] emails = (StoredEmail[]) c.getResult();

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

  public String getUIDValidity() throws MailboxException {
    return folder.getCreationTime() + "";
  }
}









