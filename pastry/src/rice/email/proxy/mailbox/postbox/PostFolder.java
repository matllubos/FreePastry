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
    try {
      PostUserAddress sender = new PostUserAddress(msg.getReturnPath().toString());

      Iterator i = msg.getRecipientIterator();
      Vector v = new Vector();

      while (i.hasNext()) {v.add(i.next());}

      PostUserAddress[] recipients = new PostUserAddress[v.size()];

      for (int j=0; j<v.size(); j++) {
        recipients[j] = (PostUserAddress) v.elementAt(j);
      }

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      Writer writer = new OutputStreamWriter(baos);

      StreamUtils.copy(msg.getContent(), writer);

      // TO DO - parse message
      EmailData data = new EmailData(baos.toByteArray());

      Email email = new Email(sender, recipients, "TEST", data, null);

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

      folder.addMessage(email, c);

      synchronized (wait) { if (result[0] == null) wait.wait(); }

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

      synchronized (wait) { if (result[0] == null) wait.wait(); }

      if (exception[0] != null) {
        throw new MailboxException(exception[0]);
      }   

      LinkedList list = new LinkedList();
      Email[] emails = (Email[]) result[0];

      System.out.println("called getMsgs, there were " + emails.length + " messages.");
      
      for (int i=emails.length-1; i>=0; i--) {
        PostMessage msg = new PostMessage(emails[i], emails.length-i, this.getFolder());
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
/*    File[] files = _folder.listFiles(UID_FILTER);
    if (files.length != 1)
      throw new MailboxException("Not one UID validity");

    String fName = files[0].getName();
    if (fName.length() < 5)
      throw new MailboxException("UID Validity too short");

    return fName.substring(4); */

    return "monkey";
  }
}