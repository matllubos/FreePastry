package rice.email.proxy.mailbox.postbox;

import rice.*;
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

  public Folder getFolder() {
    return folder;
  }

  public Folder getParent() {
    return parent;
  }

  public void delete() throws MailboxException {
    parent.removeFolder(getFullName());
  }

  public void put(MovingMessage msg) throws MailboxException {
    put(msg, new LinkedList(), null);
  }

  public void put(MovingMessage msg, List lflags, String date) throws MailboxException {
    try {
      Email email = PostMessage.parseEmail(msg.getResource());
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

      final Exception[] exception = new Exception[1];
      final Object[] result = new Object[1];
      final Object wait = "wait";

      Continuation c = new Continuation() {
        public void receiveResult(Object o) {
          synchronized (wait) {
            result[0] = o;
            wait.notify();
          }
        }

        public void receiveException(Exception e) {
          synchronized (wait) {
            exception[0] = e;
            result[0] = "result";
            wait.notify();
          }
        }
      };

      folder.addMessage(email, flags, c);

      synchronized (wait) { if ((result[0] == null) && (exception[0] == null)) wait.wait(); }

      if (exception[0] != null) {
        throw new MailboxException(exception[0]);
      }
    } catch (IOException e) {
      throw new MailboxException(e);
    } catch (InterruptedException e) {
      throw new MailboxException(e);
    } 
  }
 
  public List getMessages(MsgFilter range) throws MailboxException {
    try {
      final Object[] result = new Object[1];
      final Exception[] exception = new Exception[1];
      final Object wait = "wait";

      Continuation c = new Continuation() {
        public void receiveResult(Object o) {
          synchronized (wait) {
            result[0] = o;
            wait.notify();
          }
        }

        public void receiveException(Exception e) {
          synchronized (wait) {
            exception[0] = e;
            result[0] = "result";
            wait.notify();
          }
        }
      };

      folder.getMessages(c);

      synchronized (wait) { if ((result[0] == null) && (exception[0] == null)) wait.wait(); }

      if (exception[0] != null) {
        throw new MailboxException(exception[0]);
      }   

      LinkedList list = new LinkedList();
      StoredEmail[] emails = (StoredEmail[]) result[0];

      for (int i=0; i<emails.length; i++) {
        PostMessage msg = new PostMessage(emails[i], i+1, this.getFolder());
        if (range.includes(msg)) {
          list.addLast(msg);
        }
      }

      return list;
    } catch (InterruptedException e) {
      throw new MailboxException(e);
    }
  }

  public String getUIDValidity() throws MailboxException {
    return folder.getCreationTime() + "";
  }
}









