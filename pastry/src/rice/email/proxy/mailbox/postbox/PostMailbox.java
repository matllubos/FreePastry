package rice.email.proxy.mailbox.postbox;

import rice.email.proxy.mail.MovingMessage;

import rice.email.proxy.mailbox.*;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import rice.Continuation;

import rice.email.EmailService;
import rice.email.Folder;

/**
* This class serves as the main "glue" code between foedus and
 * the POST-based email implementation.
 */
public class PostMailbox implements Mailbox {

  // the local email service to use
  EmailService email;

  /**
  * Constructs a PostMailbox given an emailservice
   * to run off of.
   *
   * @param email The email service on the local pastry node.
   */
  public PostMailbox(EmailService email) {
    if (email == null)
      throw new IllegalArgumentException("EmailService cannot be null in PostMailbox.");

    this.email = email;
  }

  // TO DO
  public void put(MovingMessage msg) throws MailboxException {
    getFolder("INBOX").put(msg);
  }

  /**
    * Fetches a given folder name.  Currently, the valid names are:
   *   ""        root
   *   "inbox"   inbox
   *   "[\d]+"   anything else
   *
   * Note that hierarchical folders are not supported.  Also, this
   * method does block the current thread.
   *
   * @param name The name of the folder
   * @throws MailboxException If an error occurs
   * @return The specificed MailFolder.
   */
  public MailFolder getFolder(final String name) throws MailboxException {
    try {
      PostFolder root = (PostFolder) getRootFolder();

      final Folder[] folder = new Folder[1];
      final Exception[] exception = new Exception[1];

      final Object wait = "wait";

      Continuation fetch = new Continuation() {
        public void receiveResult(Object o) {
          synchronized (wait) {
            folder[0] = (Folder) o;
            wait.notify();
          }
        }

        public void receiveException(Exception e) {
          System.out.println("Could not fetch folder " + e);
          synchronized (wait) {
            exception[0] = e;
            wait.notify();
          }
        }
      };

      root.getFolder().getChildFolder(name.toLowerCase(), fetch);

      synchronized (wait) { if(folder[0] == null) wait.wait(); }

      if (exception[0] != null)
        throw exception[0];

      return new PostFolder(folder[0], root.getFolder(), email);
    } catch (Exception e) {
      throw new MailboxException(e);
    }
  }

  /**
   * Returns the root folder of the user's mailbox. Note that
   * this method blocks while fetching the folder.
   *
   * @throws MailboxException If an error occurs.
   * @return The root folder.
   */
  public MailFolder getRootFolder() throws MailboxException {
    try {
      final Folder[] folder = new Folder[1];
      final Exception[] exception = new Exception[1];

      final Object wait = "wait";

      Continuation fetch = new Continuation() {
        public void receiveResult(Object o) {
          synchronized (wait) {
            folder[0] = (Folder) o;
            wait.notifyAll();
          }
        }

        public void receiveException(Exception e) {
          System.out.println("Could not fetch folder " + e);
          synchronized (wait) {
            exception[0] = e;
            wait.notifyAll();
          }
        }
      };

      email.getRootFolder(fetch);

      synchronized (wait) { if ((folder[0] == null) && (exception[0] == null)) wait.wait(); }

      if (exception[0] != null)
        throw new MailboxException(exception[0]);

      return new PostFolder(folder[0], null, email);
    } catch (InterruptedException e) {
      throw new MailboxException(e);
    }    
  }

  private boolean isValidFolderName(String fold) {
    return true;
  }

  public void createFolder(String folder) throws MailboxException {
    if (!isValidFolderName(folder))
      throw new MailboxException("Invalid folder name.");

    try {
      final Object[] result = new Object[1];
      final Exception[] exception = new Exception[1];

      final Object wait = "wait";

      Continuation insert = new Continuation() {
        public void receiveResult(Object o) {
          synchronized (wait) {
            result[0] = o;
            wait.notifyAll();
          }
        }

        public void receiveException(Exception e) {
          System.out.println("Could not create folder " + e);
          synchronized (wait) {
            exception[0] = e;
            wait.notifyAll();
          }
        }
      };

      ((PostFolder) getRootFolder()).getFolder().createChildFolder(folder, insert);

      synchronized (wait) { if ((result[0] == null) && (exception[0] == null)) wait.wait(); }

      if (exception[0] != null)
        throw new MailboxException(exception[0]);
    } catch (Exception e) {
      throw new MailboxException(e);
    }
  }

  // TO DO: use the pattern
  public MailFolder[] listFolders(String pattern) throws MailboxException {
    try {
      PostFolder root = (PostFolder) getRootFolder();
      String[] names = root.getFolder().getChildren();
      MailFolder[] folders = new MailFolder[names.length];
      for (int i = 0; i < names.length; i++)
      {
        PostFolder folder = (PostFolder) getFolder(names[i]);
        folders[i] = new PostFolder(folder.getFolder(), root.getFolder(), email);
      }

      return folders;
    } catch (Exception e) {
      throw new MailboxException(e);
    }
  }

  public void subscribe(String fullName) throws MailboxException {
    throw new MailboxException("Subscriptions are not yet implemented...");
  }

  public void unsubscribe(String fullName) throws MailboxException {
    throw new MailboxException("Subscriptions are not yet implemented...");
  }

  public String[] listSubscriptions(String pattern) throws MailboxException {
    return new String[0];
  }
}
