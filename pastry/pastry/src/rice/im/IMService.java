package rice.im;

import java.util.*;

import rice.*;
import rice.post.*;
import rice.post.log.*;
import rice.post.messaging.*;
import rice.post.storage.*;
import rice.email.log.*;
import rice.email.messaging.*;
import rice.im.log.*;
import rice.im.messaging.*;

/**
 * This class serves as the client for the IM application that sits 
 * on top of Post.<br>
 * <br>
 * The IMService uses the observer pattern to notify other objects
 * of newly received emails.  The event generated will contain an
 * {@link IMContent} object as its argument.
 * 
 */

public class IMService extends PostClient {
    
    // the Emails Service's Post object
    public Post post;
    
    // the IMClient that is associated with this service
    IMClient client;
    
    // the contact log is the following
    Log im_log;
    
    // Hashtable of friend, family, and worker contact names
    public Vector all_contacts = new Vector();




    // Vector of online_contacts
    public Vector online_contacts = new Vector();

    // Hashtable that keeps track of whether or not the last entry in the log with key "username" has been tampered with
    public Vector last_entry = new Vector();

    // The post user address of the client that uses this service
    public final PostUserAddress pua;

    /**
     * Constructor
     *
     *@param post The Post service to use
     */
    public IMService(Post post, final PostEntityAddress pua1) {
	this.post = post;
	post.addClient(this);
	this.pua = (PostUserAddress) pua1;

	final Continuation fetch = new Continuation() {
		public void receiveResult(Object o) {
		    //Say hello to everyone in my contact list and set my state to online
		    for (int i = 0; i < all_contacts.size(); i++) {
			final Buddy [] recipients = new Buddy[1];
			recipients[0] =  (Buddy) all_contacts.get(i);
			final IMTextMessage msg = new IMTextMessage(pua, recipients, new String("initializing..."), new IMState(IMState.ENTER));
			sendMessage(msg);
		    }
		    
			
		}

		public void receiveException(Exception e) {
		    System.out.println("Fetching of contact list failed with " + e);
		}
	    };


	final Continuation listener = new Continuation() {
		public void receiveResult(Object o) {
		    getContacts(fetch);
		    System.out.println("after fetching all contacts");
		}

		public void receiveException(Exception e) {
		    System.out.println("Fetching of root log failed with " + e);
		}
	    };
	getIMLog(listener);
    }
    
    /**
     *@return the post object this serivce is using.
     */
    public Post getPost() {
	return post;
    }
    
    /**
     * Sends IMContent message to recipients. Note that we do not need any continuation
     * object because we are not storing any message. 
     */
    
    public void sendMessage(IMContent content) {
	Buddy[] recipients = content.getRecipients();
	for (int i = 0; i < recipients.length; i++) {
	    IMNotificationMessage msg = new IMNotificationMessage(content, recipients[i].getName().trim(), this);
	    if (content == null) System.out.println("Content is null in send message");
	    post.sendNotification(msg);
	    System.out.println("message sent to " + recipients[i].getName().trim());
	}
    }
    
    
    /**
     * This method is how the Post layer informs the IMService
     * layer that there is an incoming notification of a new message.
     *
     *@param nm The incoming notification.
     */
    public void notificationReceived(NotificationMessage nm) {
	
	if (client == null) {
	    System.out.println("Client is null");
	    return;
	}

	
	if (nm instanceof IMNotificationMessage) { 
	    try {
		
		IMNotificationMessage inm = (IMNotificationMessage) nm;
		
		if (inm.getIMContent().getState().equals(new IMState(IMState.ONLINE))) {
		    String name = inm.getIMContent().getSender().getName().substring(0, inm.getIMContent().getSender().getName().indexOf("@"));
		    //if (! all_contacts.contains(new Buddy(name, Buddy.FRIEND)))
		    //return;
		    client.messageReceived(nm);
		}
		else if (inm.getIMContent().getState().equals(new IMState(IMState.ONLINE_NO_DISPLAY))) {
		    String name = inm.getIMContent().getSender().getName().substring(0, inm.getIMContent().getSender().getName().indexOf("@"));
		    if (! online_contacts.contains(new String(name))) {
			online_contacts.add(new String(name));
			
		    }
		}
		else {
		    if (inm.getIMContent().getState().equals(new IMState(IMState.ENTER)))
			sendIAmAlive(inm);
		    client.stateChanged(inm);
		}
	    }
	    catch (Exception e) { System.out.println("exception with parameter" + e); }
	}
	
    }
    
	public void sendIAmAlive(IMNotificationMessage msg) {
	    String sender = msg.getIMContent().getSender().getName().substring(0, msg.getIMContent().getSender().getName().indexOf("@"));
	    Buddy [] recipients = new Buddy[1];
	    if (all_contacts.contains(new Buddy(sender, Buddy.FRIEND))) {
		recipients[0] = new Buddy(sender, Buddy.FRIEND);
		IMTextMessage imt = new IMTextMessage(pua, recipients, new String(""), new IMState(IMState.ONLINE_NO_DISPLAY));
		sendMessage(imt);
	    }
	    
	     if (all_contacts.contains(new Buddy(sender, Buddy.FAMILY))) {
		recipients[0] = new Buddy(sender, Buddy.FAMILY);
		IMTextMessage imt = new IMTextMessage(pua, recipients, new String(""), new IMState(IMState.ONLINE_NO_DISPLAY));
		sendMessage(imt);
	     }
	     
	     if (all_contacts.contains(new Buddy(sender, Buddy.WORKER))) {
		recipients[0] = new Buddy(sender, Buddy.WORKER);
		IMTextMessage imt = new IMTextMessage(pua, recipients, new String(""), new IMState(IMState.ONLINE_NO_DISPLAY));
		sendMessage(imt);
	     }
	     
	    
	
	}
	    
	    

    /**
     * This method allows an IMClient client to register itself with this service
     *@param obj The object that is registering with this client
     */
    public void register(IMClient client) {
	if (client != null)
	    this.client = client;
    
    }
    
    
	
    /**
     * This method adds name to the contact list by modifying the logs that are on the network. Returns true if successful.
     *
     *@param name the name that is to be added to the contact list
     *@param command the continuation that is passed in and whose receiveResult method is called as soon as
     * writing to the logs is completed. 
     
     */ 
    public void addContact(Buddy buddy, final Continuation command) {
	
	final Continuation fetch = new Continuation() {
		public void receiveResult(Object o) {
		    AddContactLogEntry acle = (AddContactLogEntry) o;
		    if (! all_contacts.contains(acle.getBuddy()) ) {
			all_contacts.add(new Buddy(acle.getBuddy()));
			command.receiveResult(acle.getBuddy());
		    }
		    else command.receiveResult(null);
		}

		public void receiveException(Exception e) {
		    System.out.println("Contact could not be fetched with exception " + e);
		}
	    };
	
	im_log.addLogEntry(new AddContactLogEntry(buddy), fetch);
    }
    
    /**
     * This method removes name from the contact list by modifying the logs that are on the network. Returns true if successful.
     *
     *@param name the name that is to be removed from the contact list
     *@param command the Continuation, whose receiveResult method is called as soon as the logs are removed from the network
     */ 
    public void removeContact(Buddy buddy, final Continuation command) {
	    
	    final Continuation fetch = new Continuation() {
		    public void receiveResult(Object o) {
			RemoveContactLogEntry rcle = (RemoveContactLogEntry) o;
			all_contacts.remove(rcle.getBuddy());
			command.receiveResult(rcle.getBuddy());
		    }
		    
		    public void receiveException(Exception e) {
			System.out.println("Contact could not be fetched with exception " + e);
		    }
		};
	    
		    im_log.addLogEntry(new RemoveContactLogEntry(buddy), fetch);
	}

    
    /**
     * This method retrieves all the contacts for this IMService that are in the log entries.
     */
    public void getContacts(final Continuation command) {
	all_contacts = new Vector();
	last_entry = new Vector();
	Continuation fetch = new Continuation() {
		public void receiveResult(Object o) {
		    LogEntry topEntry = (LogEntry) o;
		    if (topEntry != null) {
			if (topEntry instanceof AddContactLogEntry) {
			    AddContactLogEntry tEntry = (AddContactLogEntry) topEntry;
			    if (! last_entry.contains(tEntry.getBuddy())) {
				last_entry.add(new Buddy (tEntry.getBuddy()));
				all_contacts.add(new Buddy (tEntry.getBuddy()));
			    }
			    topEntry.getPreviousEntry(this);
			}

			else if (topEntry instanceof RemoveContactLogEntry) {
			    RemoveContactLogEntry tEntry = (RemoveContactLogEntry) topEntry;
			     if (! last_entry.contains(tEntry.getBuddy())) {
				 last_entry.add(new Buddy (tEntry.getBuddy()));
				 all_contacts.remove(new Buddy (tEntry.getBuddy()));
			     }
			    topEntry.getPreviousEntry(this);
			}
		    }
		    else 
			command.receiveResult(new Boolean(true));
		}

		public void receiveException(Exception e) {
		    System.out.println("Exception " + e + " occurred here while trying to read LogEntry of im_log");
		    e.printStackTrace();
		}
		    };
	im_log.getTopEntry(fetch);
    }
	
    public void sayGoodBye() {
	for (int i = 0; i < online_contacts.size(); i++) {
	    Buddy recipients[] = new Buddy[1];
	    recipients[0] = (Buddy) online_contacts.get(i);
	    IMTextMessage msg = new IMTextMessage(pua, recipients, "(null)", new IMState(IMState.EXIT));
	    sendMessage(msg);
	}
    }
    

    public void getIMLog(final Continuation command) {
	
    if (im_log != null) {
      command.receiveResult(im_log);
      return;
    }
    
    // find the Client Id for this client
    final PostClientAddress pca = PostClientAddress.getAddress(this);
    
    final Continuation getLog = new Continuation() {
	    public void receiveResult(Object o) {
		PostLog mainLog = (PostLog) o;
		
		if (mainLog == null) {
		    System.out.println("postlog was null");
		    System.exit(1);
		} else {
		    // use the main root log to try to fetch the ePost root log
		    LogReference imLogRef = mainLog.getChildLog(pca);
		    
		    // if the IM chat application does not have a log yet, add one
		    if (imLogRef == null) {
			
			// ...and then fetch and assign it
			final Continuation fetch = new Continuation() {
				public void receiveResult(Object o) {
				    try {
					im_log = (Log) o;
					im_log.setPost(post);
					command.receiveResult(new Boolean(true));
				    }
				    catch (Exception e) {
					e.printStackTrace();
					System.exit(1);
				    }
				}
				public void receiveException(Exception e) {
				    command.receiveException(e);
				}
			    };
				       

			// first store the new log...
			Continuation store = new Continuation() {
				public void receiveResult(Object o) {
				      try {
					  LogReference imLogRef = (LogReference) o;
					  post.getStorageService().retrieveSigned(imLogRef, fetch);
				      } catch (ClassCastException e) {
					  command.receiveException(new ClassCastException("In getLog, expected a Log, got a " + o.getClass()));
				      }
				}
				
				public void receiveException(Exception e) {
				    command.receiveException(e);
				}
			    };
			
			Log imRootLog = new Log(pca, post.getStorageService().getRandomNodeId(), post);
			mainLog.addChildLog(imRootLog, store);
		    } else {
			
			// fetch the log folder and assign to it
			Continuation fetch = new Continuation() {
				public void receiveResult(Object o) {
				    
				    try {
					    im_log = (Log) o;
					    im_log.setPost(post);
					    command.receiveResult(new Boolean(true));
				    }
				    
				    catch (ClassCastException e) {
					command.receiveException(new ClassCastException("In getLog, expected a Log, got a " + o.getClass()));
				    }
				}
				
				public void receiveException(Exception e) {
				    command.receiveException(e);
				}
			    };
			
			post.getStorageService().retrieveSigned(imLogRef, fetch);
		    }
		}
	    }
	    
	    public void receiveException(Exception e) {
		command.receiveException(e);
	    }
	};
    
    post.getPostLog(getLog);
    }
    

    

}





