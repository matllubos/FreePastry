package rice.email.test;

import rice.*;
import rice.email.*;
import rice.email.log.*;
import rice.post.*;
import java.util.*;

/**
 * Test case that I was initially hoping would be simple.  Performs a fairly extensive 
 * test of the Folder functionality.
 * - fetch's the root folder
 * - adds n subFolders to the root folder
 * - adds n subSubFolders to each of the subFolders
 * - places an email in each of the created folders
 * - adds enough emails to one folder to trigger a snapshot 
 * - prints out the contents and history of each folder
 */

public class SimpleFolderTest extends Thread {
  public static final int START = 0;
  public static final int GOT_ROOT = 10;
  public static final int MAKING_SUBS = 20;
  public static final int EMAIL_INIT = 30;
  public static final int EMAIL_ADD = 40;
  public static final int SNAPSHOT_1 = 50;
  public static final int CHECK_EMAILS_1A = 60;
  public static final int CHECK_EMAILS_1B = 63;
  public static final int CHECK_EMAILS_1C = 66;
  public static final int CHECK_EVENTS_1A = 70;
  public static final int CHECK_EVENTS_1B = 73;
  public static final int CHECK_EVENTS_1C = 76;
  public static final int CHECK_FOLDERS = 80;
  public static final int ROTATE_A = 90;
  public static final int ROTATE_B = 95;
  public static final int DELETE_1A = 100;
  public static final int DELETE_1B = 103;
  public static final int DELETE_1C = 106;
  public static final int DELETE_2A = 110;
  public static final int DELETE_2B = 113;
  public static final int DELETE_2C = 116;
  public static final int DELETE_3  = 120;  

  
  public static final int MAX_SUB_FOLDERS = 2;
  public static final int MAX_SUB_SUB_FOLDERS = MAX_SUB_FOLDERS * MAX_SUB_FOLDERS;

  // the sender
  private EmailService _sender;
  // the root folder for the sender
  private static Folder _rootFolder;
  // the current state of the program
  private static int state = START;

  // counters for the state of the Folder creation
  int subFolderState;
  int currentSubFolder;

  // the subFolders
  static Folder[] subFolders;

  // the subSubFolders
  public static Folder[]  subSubFolders;

  // temp Folder for all sorts of uses
  Folder tempFolder;

  // base name for Folder creation
  String folderName = "Folder";

  // state for adding emails to the Folders
  static public Vector targets;
  static public int emailCount = 0;

  static public Vector addedEmails = new Vector();


  public SimpleFolderTest(String name) {
    super(name);
  }

  public void run() {
    // start the thread from at the appropriate start in the execution
    if (state == START) {
      System.out.println("Starting tests");
      startTest();
    }
    else if (state == SNAPSHOT_1) {
      System.out.println("Doing snapshots");
      testSnapShot1();
    }
    else if (state == ROTATE_A) {
      System.out.println("Rotating messages");
      rotateMessages();
    }
    else {
      System.out.println("deleting messages and folders");
      deleteMessages();
    }
    System.out.println("Finished with Folder Tests");
  }

  /**
   * Performs initialization, starts the test up.
   */
  public void startTest() {
    // set the initial state
    state = START;    
    subFolderState = 0;
    currentSubFolder = 0;
    subFolders = new Folder[MAX_SUB_FOLDERS];
    subSubFolders = new Folder[MAX_SUB_SUB_FOLDERS];
    testInit();
  }

  /**
   * Test the creation and fetching of the root Folder.
   */
  public void testInit() {
    if (state == START) {
      // Create the sender
      EmailService[] services;
      EmailTest emailTest = new EmailTest();
      services = emailTest.createEmailServices(new String[]{"user1"}, 2000);		    
      _sender = services[0];
      if (_sender == null) {
	System.out.println("Could not create an EmailService to work with; Bailing.");
	return;
      }

      // JM just until we get some more continuations into the system
      try{
	System.out.println("Waiting...");
	Thread.sleep(3000);
      } catch (InterruptedException e) {
	System.out.println("INTERRUPTED: " + e);
      }

      // try to get the root Folder
      state = GOT_ROOT;
      Continuation command = new SimpleFolderCont();      
      _sender.getRootFolder(command);
    }

    else if (state == GOT_ROOT) {
      if (_rootFolder == null) {
	System.out.println("Root Folder was null after getRoot. Very bad, exitting.");
	return;
      }

      // once we have the name of the root, print it out
      System.out.println("Name of the root Folder: " + _rootFolder.getName());

      // create a new Folder under the root
      state = MAKING_SUBS;
      tempFolder = _rootFolder;
      testFolderCreation();
    }
  }

  /**
   * Test Folder creation.  Makes 3 subFolders, and then 3 subFolders for each of them.
   */
  public void testFolderCreation() {
    // update the state
    if (state == MAKING_SUBS) {	
      // if done with a subFolder, change the root and update the state
      if (subFolderState >= MAX_SUB_FOLDERS) { 
	// if there are folders left to create,  change the state to start the next subFolder
	if (currentSubFolder < MAX_SUB_FOLDERS) {
	  System.out.println("Moving on to making subSubFolder " + currentSubFolder);
	  subFolderState = 0;
	  tempFolder = subFolders[currentSubFolder];
	  currentSubFolder = currentSubFolder + 1;
	  folderName = "Folder " + (new Integer(currentSubFolder)).toString();
	}
	// otherwise we are done with all of folder creation, so change the state to start the next section
	else {
	  state = EMAIL_INIT;
	}
      }
    }

    // add a new folder
    if (state == MAKING_SUBS) {	
      // make a subFolder for the current root
      System.out.println("Making subFolder " + subFolderState);
      Continuation command = new SimpleFolderCont();
      String newFolderName = folderName + " " +  (new Integer(subFolderState)).toString();
      subFolderState = subFolderState + 1;
      tempFolder.createChildFolder(newFolderName, command);
    }

    // otherwise, print result and move to next section
    else if (state == EMAIL_INIT) {
      // or if finished, move on to the next test
      System.out.println("Finished Creating the Folders");
      // print out the names of the subFolders
      String[] childNames = _rootFolder.getChildren();
      System.out.println("Number of sub folders is " + childNames.length);
      for (int i = 0; i < childNames.length; i++) {
	System.out.println(childNames[i]);
      }
      
      // print out the names of the subSubFolders
      for (int i = 0; i < subFolders.length; i++) {
	childNames = subFolders[i].getChildren();
	System.out.println("Number of subSub folders is " + childNames.length);
	for (int j = 0; j < childNames.length; j++) {
	  System.out.println(childNames[j]);
	}
      }
      testEmailStorage();
    }
    else {
      System.out.println("Invalid state in testFolderCreation");
    }
  }

  
  /**
   * Adds emails to the created Folders.
   */
  public void testEmailStorage() {
    // do initialization if necesary
    if (state == EMAIL_INIT) {
      System.out.println("Initializing the EmailStorage state");
      // make a nice list of the Folders to add to
      emailCount = 0;
      targets = new Vector();
      targets.add(_rootFolder);
      for (int i = 0; i < subFolders.length; i++) {
	targets.add(subFolders[i]);
      }
      for (int i = 0; i < subSubFolders.length; i++) {
	targets.add(subSubFolders[i]);
      }
      state = EMAIL_ADD;
    }
    // Add another email
    if (state == EMAIL_ADD) {
      // if there is room for another email, add one
      if (emailCount < targets.size()) {
	System.out.println("Adding email " + emailCount);

	// make the Email  and add it to the current Folder
	String postFix = (new Integer(emailCount)).toString();
	Email newEmail = makeEmail("TestSender " + postFix, "TestReciever " + postFix, "First Email for Folder " + postFix, "");
	Continuation cont = new AddEmailCont();
	// record the Email in our list of emails that have been added to Folders. For convenience
	addedEmails.add(newEmail);
	// begin adding the email to the Folder
	((Folder)(targets.get(emailCount))).addMessage(newEmail, cont);
      }
      // otherwise change the state and go on to the next test
      else {
	System.out.println("Finished adding the emails to each Folder, moving on to Snapshot");
	state = SNAPSHOT_1;
	emailCount = 0;
	SimpleFolderTest nextTest = new SimpleFolderTest("Another SFT");
	nextTest.start();
	try {
	  stop();
	  wait();
	} catch (Exception e) {
	}
      }
    }
  }

  /**
   * Adds emails to a Folder until a snapShot node is created.
   */
  public void testSnapShot1() {
    // if we have not yet added enough emails to make a new SnapShot
    if (emailCount < Folder.COMPRESS_LIMIT+1 - 4) {
      // add another email.
      String postFix = (new Integer(emailCount)).toString();
      Email newEmail = makeEmail("SS-Sender " + postFix, "SS-Receiver " + postFix, "SS message " + postFix, "Make me a snapshot!");
      Continuation cont = new AddEmailCont();
      System.out.println("Adding snapshot email " + emailCount);
      subFolders[0].addMessage(newEmail, cont);
    }
    // otherwise we are done, and ready to move to the next test
    else {
      state = CHECK_EMAILS_1A;
      emailCount = 0;
      testEmails1();
    }
  }

  /**
   * Checks that each of the folders contains the appropriate emails.
   */
  public void testEmails1() {
    System.out.println("Entering testEmails1. EmailCount is: " + emailCount + ", and state is:" + state);
    if (state == CHECK_EMAILS_1A) {
      // if we are done with the root folder
      if (emailCount > 0) {
	emailCount = 0;
	state = CHECK_EMAILS_1B;
	testEmails1();
      } else {
	System.out.println("\nRoot Folder contains the emails:" );
	_rootFolder.getMessages(new AddEmailCont());
      }
      
    } else if (state == CHECK_EMAILS_1B) {
      // if we are done with the subFolders, move on to the next part
      if (emailCount >= subFolders.length) {
	state = CHECK_EMAILS_1C;
	emailCount = 0;
	testEmails1();
      } else {
	System.out.println("\nSub Folder " + emailCount + "  contains the emails:" );
	subFolders[emailCount].getMessages(new AddEmailCont());
      }
      
    } else {
      // if we are done with the subSubFolders, move on to the next part
      if (emailCount >= subSubFolders.length) {
	state = CHECK_EVENTS_1A;
	emailCount = 0;
	System.out.println("Done with testEmails1, emailCount is " + emailCount + 
			   ", subFolder length is " + subFolders.length + 
			   ", and subSubFolder length is " + subSubFolders.length);	
	testEvents1();
      } else {
	System.out.println("\nSub Sub Folder " + emailCount + "  contains the emails:" );
	subSubFolders[emailCount].getMessages(new AddEmailCont());
      }
    }
  }
  
  public void testEvents1() {
    System.out.println("Entering testEvents1. EmailCount is: " + emailCount + ", and state is:" + state);
    if (state == CHECK_EVENTS_1A) {
      // if we are done with the root folder
      if (emailCount > 0) {
	emailCount = 0;
	state = CHECK_EVENTS_1B;
	testEvents1();
      } else {
	System.out.println("\nRoot Folder contains the following events:" );
	_rootFolder.getCompleteEventLog(new AddEmailCont());
      }
      
    } else if (state == CHECK_EVENTS_1B) {
      // if we are done with the subFolders, move on to the next part
      if (emailCount >= subFolders.length) {
	state = CHECK_EVENTS_1C;
	emailCount = 0;
	testEvents1();
      } else {
	System.out.println("\nSub Folder " + emailCount + "  contains the events:" );
	subFolders[emailCount].getCompleteEventLog(new AddEmailCont());
      }
      
    } else {
      // if we are done with the subSubFolders, move on to the next part
      if (emailCount >= subSubFolders.length) {
	state = CHECK_FOLDERS;
	emailCount = 0;
	System.out.println("Done with testEvents1, emailCount is " + emailCount + 
			   ", subFolder length is " + subFolders.length + 
			   ", and subSubFolder length is " + subSubFolders.length);	
	testFolders();
      } else {
	System.out.println("\nSub Sub Folder " + emailCount + "  contains the events:" );
	subSubFolders[emailCount].getCompleteEventLog(new AddEmailCont());
      }
    }
  }

  private void printChildFolders(String[] children) {
    for (int i = 0; i < children.length; i++) {
      System.out.println(children[i]);
    }
    System.out.println("");
  }

  public void testFolders() {
    System.out.println("\n\nEntering testFolders!!! ");
    // check the root Folder
    System.out.println("RootFolder's child folders");
    printChildFolders(_rootFolder.getChildren());

    // check the subFolders
    System.out.println("subFolder's child folders");
    for (int i = 0; i < subFolders.length; i++) {
      printChildFolders(subFolders[i].getChildren());
    }

    // check the subSubFolders
    System.out.println("subSubFolder's child folders");
    for (int i = 0; i < subSubFolders.length; i++) {
      printChildFolders(subSubFolders[i].getChildren());
    }

    // start a new thread to continue the work.  This allows us to
    // reset the stack frame and avoid stack overflow errors    
    emailCount = 0;
    state = ROTATE_A;
    SimpleFolderTest nextTest = new SimpleFolderTest("Another SFT");
    nextTest.start();
    try {
      stop();
      wait();
    } catch (Exception e) {
    }
  }

  /**
   * Rotates messages between the Folders.  Basically moves each message to the next Folder
   * at its level in the tree.
   */
  public void rotateMessages() {
    System.out.println("entering rotate messages. State: " + state + " emailCount: " + emailCount+"\n");

    // rotate messages among the subFolder's
    if (state == ROTATE_A) {
      System.out.println("rotating subFolder message " + emailCount);
      // if we are done with the subFolder's, move on to the subSubFolders
      if (emailCount >= subFolders.length) {
	emailCount = 0;
	state = ROTATE_B;
	rotateMessages();

      // otherwise perform an actual rotation
      } else {
	Email sourceEmail = (Email)addedEmails.get(emailCount+1);
	Folder sourceFolder = subFolders[emailCount];
	Folder targetFolder = subFolders[(emailCount+1) % subFolders.length];
	System.out.println("SourceEmail: " + sourceEmail + "  sourceFolder: " + sourceFolder + "  targetFolder: " + targetFolder);
	sourceFolder.moveMessage(sourceEmail, targetFolder, new AddEmailCont());
      }

    // rotate messages among the subSubFolder's
    } else {
      System.out.println("rotating subSubFolder message " + emailCount);
      // if we are done with the subSubFolder's, move on to the next test
      if (emailCount >= subSubFolders.length) {
	emailCount = 0;
	state = DELETE_1A;
	System.out.println("\n\n\n\n");
	deleteMessages();

      // otherwise perform an actual rotation
      } else {
	Email sourceEmail = (Email)addedEmails.get(emailCount + 1 + subFolders.length);
	Folder sourceFolder = subSubFolders[emailCount];
	Folder targetFolder = subSubFolders[(emailCount+1) % subSubFolders.length];
	sourceFolder.moveMessage(sourceEmail, targetFolder, new AddEmailCont());
      }
    }
  }
  
  /**
   * Deletes the messages and Folders, printing contents as it goes along.
   */
  public void deleteMessages() {
    System.out.println("Deleting messages.  State is " + state + "  emailCount is " + emailCount+"\n");
    // first remove the messages from the subSubFolders
    if (state == DELETE_1A) {
      // if we are done with this part, move on to the next
      if (emailCount >= subSubFolders.length) {
	emailCount = 0;
	state = DELETE_1B;
	deleteMessages();

      } else {
	// find the index of the rotated email among the subSubFolders
	int offset;
	if (emailCount == 0) {
	  offset = 3;
	} else {
	  offset = emailCount - 1;
	}
	// get the email from the list of stored emails
	Email targetEmail = (Email)addedEmails.get(offset + 1 + subFolders.length);
	System.out.println("Deleting message " + offset);	
	// remove the email
	subSubFolders[emailCount].removeMessage(targetEmail, new AddEmailCont());
      }

    // print the new contents of the subSubFolders      
    } else if (state == DELETE_1B) {      
      // if we are done with this part, move on to the next
      if (emailCount >= subSubFolders.length) {
	emailCount = 0;
	state = DELETE_1C;
	SimpleFolderTest nextTest = new SimpleFolderTest("Another SFT");
	nextTest.start();
	try {
	  stop();
	  wait();
	} catch (Exception e) {
	}
      } else {
	System.out.println("Checking Folder " + emailCount);
	subSubFolders[emailCount].getCompleteEventLog(new AddEmailCont());
      }
    // then remove the subFolders themselves    
    } else if (state == DELETE_1C) {      
      // if we are done with this part, move on to the next
      if (emailCount >= subSubFolders.length) {
	emailCount = 0;
	state = DELETE_2A;
	deleteMessages();
      } else {
	System.out.println("Removing Folder " + emailCount);	
	subFolders[(emailCount / 2)].removeFolder(subSubFolders[emailCount].getName(), new AddEmailCont());
      }
    
    // first remove the messages from the subFolders
    } else if (state == DELETE_2A) {
      // if we are done with this part, move on to the next
      if (emailCount >= subFolders.length) {
	emailCount = 0;
	state = DELETE_2B;
	deleteMessages();

      } else {
	// find the index of the rotated email among the subFolders
	int offset;
	if (emailCount == 0) {
	  offset = 1;
	} else {
	  offset = 0;
	}
	// get the email from the list of stored emails
	Email targetEmail = (Email)addedEmails.get(offset + 1);
	System.out.println("Deleting message " + offset);	
	// remove the email
	subFolders[emailCount].removeMessage(targetEmail, new AddEmailCont());
      }

    // print the new contents of the subFolders      
    } else if (state == DELETE_2B) {      
      // if we are done with this part, move on to the next
      if (emailCount >= subFolders.length) {
	emailCount = 0;
	state = DELETE_2C;
	SimpleFolderTest nextTest = new SimpleFolderTest("Another SFT");
	nextTest.start();
	try {
	  stop();
	  wait();
	} catch (Exception e) {
	}
      } else {
	System.out.println("Checking Folder " + emailCount);
	subFolders[emailCount].getCompleteEventLog(new AddEmailCont());
      }
    // then remove the subFolders themselves    
    } else if (state == DELETE_2C) {      
      // if we are done with this part, move on to the next
      if (emailCount >= subFolders.length) {
	emailCount = 0;
	state = DELETE_3;
	deleteMessages();
      } else {
	System.out.println("Removing Folder " + emailCount);	
	_rootFolder.removeFolder(subFolders[emailCount].getName(), new AddEmailCont());
      }    
    } else {
      System.out.println("Done!");
    }
    
    // next remove the messages from the subFolders

    // print the new contents of the subFolders


    // then remove the subFolders themselves
   
    // print the contents of the root folder

    // done
  }
    
    /**
   * Creates an email from the given Strings.
   */
    private Email makeEmail(String send, String recip, String sub, String bodyText) {
    PostUserAddress sender = new PostUserAddress(send);
    PostEntityAddress[] recipients = new PostEntityAddress[1];
    recipients[0] = new PostUserAddress(recip);
    String subject = sub;
    EmailData body = new EmailData((new String("The Body.\n" + bodyText)).getBytes());
    EmailData[] attachments = new EmailData[0];
    
    // make the Email itself and add it to the current Folder
    return new Email(sender, recipients, subject, body, attachments);
  } 

  /**
   * Gets the RootFolder and subFolders.
   */
  protected class SimpleFolderCont implements Continuation {
    public SimpleFolderCont() {}
    public void start() {}
    public void receiveResult(Object o) {      
      System.out.println("SimpleFolderCont received result. State is " + state + " " + currentSubFolder + " " + subFolderState);
      System.out.println("Adding new Folder " + (Folder)o);

      if (state == GOT_ROOT) {
	_rootFolder = (Folder)o;
	testInit();
      }
      else if (state == MAKING_SUBS) {
	if (currentSubFolder == 0) {
	  subFolders[subFolderState-1] = (Folder)o;
	}
	else {
	  subSubFolders[subFolderState-1 + ((currentSubFolder - 1) * MAX_SUB_FOLDERS)] = (Folder)o;
	}
	testFolderCreation();
      }
      else {
	System.out.println("invalid state in SimpleFolderCount: " + state);
      }
    }

    public void receiveException(Exception e) {
      e.printStackTrace();
      System.out.println("Exception " + e + "  occured while trying to get Folder for SimpleFolderTest.");
    }
  }

  /**
   * Adds more emails to the Folders.
   */   
  protected class AddEmailCont implements Continuation {
    public AddEmailCont() {}
    public void start() {}
    public void receiveResult(Object o) {      
      emailCount = emailCount + 1;
      if (state == EMAIL_ADD) {
	testEmailStorage();
      }
      else if (state == SNAPSHOT_1) {
	testSnapShot1();
      }
      else if ((state == CHECK_EMAILS_1A) || (state == CHECK_EMAILS_1B) || (state == CHECK_EMAILS_1C)) {
	Email[] results = (Email[])o;
	for (int i = 0; i < results.length; i++) {
	  System.out.println(results[i]);
	}
	testEmails1();
      }
      else if ((state == CHECK_EVENTS_1A) || (state == CHECK_EVENTS_1B) || (state == CHECK_EVENTS_1C)) {
	Object[] results = (Object[])o;
	for (int i = 0; i < results.length; i++) {
	  System.out.println(results[i]);
	}
	testEvents1();
      }
      else if ((state == ROTATE_A) || (state == ROTATE_B)) {
	System.out.println("rotated message " + emailCount);
	rotateMessages();
      }
      else if ((state == DELETE_1A) || (state == DELETE_1B) || (state == DELETE_1C) ||
	       (state == DELETE_2A) || (state == DELETE_2B) || (state == DELETE_2C) ||
	       (state == DELETE_3)) {
	System.out.println("delete message cont");
	if ((state == DELETE_1B) || (state == DELETE_2B)) {
	  Object[] results = (Object[])o;
	  for (int i = 0; i < results.length; i++) {
	    System.out.println(results[i]);
	  }
	}
	deleteMessages();
      }
      else {
	System.out.println("Invalid state: " + state);
      }
    }

    public void receiveException(Exception e) {
      e.printStackTrace();
      System.out.println("Exception " + e + "  occured while trying to get Folder for SimpleFolderTest.");
    }
  }


  public static void main(String[] args) throws InterruptedException {

    SimpleFolderTest test1 = new SimpleFolderTest("SimpleTest1");

    test1.start();

    Thread.sleep(4000);
  }
}
