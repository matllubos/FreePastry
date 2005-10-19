package rice.email.proxy.mailbox.postbox;

import rice.email.proxy.mail.MovingMessage;

import rice.email.proxy.mailbox.*;
import rice.email.proxy.imap.*;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

import java.util.*;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.*;

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

    Hashtable subscriptions = new Hashtable();

  // a cache of the previously-fetch folders
  Hashtable folders;

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
    this.folders = new Hashtable();
  }

  // TO DO
  public void put(MovingMessage msg) throws MailboxException {
    getFolder("INBOX").put(msg, null, null);
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
  public MailFolder getFolder(String name) throws MailboxException {
    if (name.trim().toLowerCase().equals("inbox")) {
      name = EmailService.INBOX_NAME;
    }

    String[] names = ((PostFolder) getRootFolder()).getFolder().getChildren();
    Arrays.sort(names);
    if (Arrays.binarySearch(names, name) < 0) {
      throw new MailboxException("Folder " + name + " does not exist!");
    }
    
        if (folders.get(name) != null) {
          return (MailFolder) folders.get(name);
        }

    System.out.println("Getting " + name);
    
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

      root.getFolder().getChildFolder(name, fetch);

      synchronized (wait) { if ((folder[0] == null) && (exception[0] == null)) wait.wait(); }

      if (exception[0] != null)
        throw exception[0];

      folders.put(name, new PostFolder(folder[0], root.getFolder(), email));
      
      return (MailFolder) folders.get(name);
    } catch (Exception e) {
      e.printStackTrace();
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
    if (folder.trim().toLowerCase().equals("inbox")) {
      folder = EmailService.INBOX_NAME;
    }

    System.out.println("Creating " + folder);
    
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

 public void deleteFolder(String folder) throws MailboxException {
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

      ((PostFolder) getRootFolder()).getFolder().removeFolder(folder.toLowerCase(), insert);

      synchronized (wait) { if ((result[0] == null) && (exception[0] == null)) wait.wait(); }

      if (exception[0] != null)
        throw new MailboxException(exception[0]);
    } catch (Exception e) {
      throw new MailboxException(e);
    }
  }

  public MailFolder[] listFolders(String pattern) throws MailboxException {
    PostFolder root = (PostFolder) getRootFolder();
    String[] names = root.getFolder().getChildren();
    Vector folders = new Vector();

    pattern = pattern.replaceAll("\\*", ".*").replaceAll("\\%", ".*");

    for (int i = 0; i < names.length; i++) {
      PostFolder folder = (PostFolder) getFolder(names[i]);

      if (folder.getFullName().matches(pattern))
        folders.add(new PostFolder(folder.getFolder(), root.getFolder(), email));
    }

    return (MailFolder[]) folders.toArray(new MailFolder[0]);
  }    

  public void subscribe(ImapConnection conn, String fullName) throws MailboxException {
    try {
      if (! subscriptions.containsKey(conn)) {
        subscriptions.put(conn, new Vector());
      }

      if (!((Vector) subscriptions.get(conn)).contains(fullName)) {
        ((Vector)subscriptions.get(conn)).add(fullName);
      }
    } catch (Exception e) {
      throw new MailboxException(e);
    }
  }

  public void unsubscribe(ImapConnection conn, String fullName) throws MailboxException {
    if (((Vector) subscriptions.get(conn)).contains(fullName)) {
      ((Vector) subscriptions.get(conn)).remove(fullName);
    }
  }

  public String[] listSubscriptions(ImapConnection conn, String pattern) throws MailboxException {
    if (subscriptions.containsKey(conn)) {
      Vector subList = (Vector) subscriptions.get(conn);

      pattern = pattern.replaceAll("\\*", ".*").replaceAll("\\%", ".*");

      for (int i=subList.size()-1; i>=0; i--) {
        String str = (String) subList.elementAt(i);

        if (! str.matches(pattern)) {
          subList.remove(str);
        }
      }
        
      return (String[]) subList.toArray(new String[0]);
    } else {
      return new String[0];
    }
  }
}




