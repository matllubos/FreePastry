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
    if (folders.get(name.toLowerCase()) != null) {
      return (MailFolder) folders.get(name.toLowerCase());
    }
    
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

      synchronized (wait) { if ((folder[0] == null) && (exception[0] == null)) wait.wait(); }

      if (exception[0] != null)
        throw exception[0];
    
      folders.put(name.toLowerCase(), new PostFolder(folder[0], root.getFolder(), email));

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

      ((PostFolder) getRootFolder()).getFolder().createChildFolder(folder.toLowerCase(), insert);

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

  // TO DO: use the pattern
  public Vector listFolders(String pattern) throws MailboxException {
    try {
	PostFolder root = (PostFolder) getRootFolder();
	String[] names = root.getFolder().getChildren();
	//      MailFolder[] folders = new MailFolder[names.length];
	Vector folders = new Vector();
	
	for (int i = 0; i < names.length; i++) {

		PostFolder folder = (PostFolder) getFolder(names[i]);

		if (pattern.endsWith("*") || pattern.equals("*")) {
		    String p = pattern.replaceAll("\\*", ".*");
		    if (((String)folder.getFullName()).matches(p)) 
			folders.add(new PostFolder(folder.getFolder(), root.getFolder(), email));
		}

		if (pattern.endsWith("%")) {
		    String p = pattern.replaceAll("\\%", ".*");
		    if (((String)folder.getFullName()).matches(p)) 
			folders.add(new PostFolder(folder.getFolder(), root.getFolder(),email));
		}

		else if (folder.getFullName().equalsIgnoreCase(pattern)) {
		    folders.add(new PostFolder(folder.getFolder(), root.getFolder(), email));
		 
		}
	}
	return folders;
    } catch (Exception e) {
	throw new MailboxException(e);
    }
  }    

  public void subscribe(ImapConnection conn, String fullName) throws MailboxException {
      try {

	  if (!subscriptions.containsKey(conn)) {
	      subscriptions.put(conn, new Vector());
	  }
	 
	  if (!((Vector)subscriptions.get(conn)).contains(fullName)) {
	      ((Vector)subscriptions.get(conn)).add(fullName);
	  }

      } catch (Exception e) {
	  throw new MailboxException(e);
      }
  }

  public void unsubscribe(ImapConnection conn, String fullName) throws MailboxException {
      //throw new MailboxException("Subscriptions are not yet implemented...");
      try{
	  if (((Vector)subscriptions.get(conn)).contains(fullName)) {
	      ((Vector)subscriptions.get(conn)).remove(fullName);
	  }      
      } catch (Exception e) {
	  throw new MailboxException(e);
      }
  }

  public String[] listSubscriptions(ImapConnection conn, String pattern) throws MailboxException {
      try {
	  String[] nameList;
	  if (!subscriptions.containsKey(conn)) {
	      subscriptions.put(conn, new Vector());
	      nameList = new String[1];
	      nameList[0] = "";
	  }
	  else {
	      Vector subList = (Vector)subscriptions.get(conn);
	     
	       nameList = new String[subList.size()];
	      for (int i=0; i < subList.size(); i++) {
		  nameList[i] = (String)subList.elementAt(i);	 
	      }
	  }	  
	  return nameList;
	  
      } catch (Exception e) {
	  throw new MailboxException(e);
      }
  }
}




