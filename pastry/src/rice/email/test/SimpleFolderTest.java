package rice.email.test;

import rice.*;
import rice.email.*;
import rice.email.log.*;
import rice.post.*;
import java.util.*;

/**
 * Test case that I had hoped would be simple.  Performs a fairly extensive 
 * test of the Folder functionality.  There are two items of interest
 * in this class.  One is that multiple threads are used to carry out
 * the test.  This is because the system is in CPS style, that is, the
 * function calls are linear and progress all the way down to the end
 * of the execution, and then pop all the way back up.  If you perform any
 * significant work, this will cause a stackOverflowError.  To prevent
 * this, new threads are created to reset the stack.  The second item
 * of interest is that the test has been scaled back from its original
 * design.  The original design propresed to add something on the
 * order of 1000 emails in a variety of folders and configurations,
 * this was abandoned since a) the number of new threads needed for
 * this would have been overly complex, and b) the time to add new
 * emails would have made this test take roughly an hour, and difficult
 * to debug.  So, a simpler version was created.  The simpler version
 * does the following test sequence:
 * - fetch's the root folder
 * - adds n subFolders to the root folder
 * - adds n subSubFolders to each of the subFolders
 * - places an email in each of the created folders
 * - adds enough emails to one folder to trigger a snapshot 
 * - prints out the contents and history of each folder
 * - rotates the emails among their level in the tree
 * - removes the messages and their folders, printing out the state of
 *   each Folder as it goes along
 * 
 * @author Joe Montgomery
 */
public class SimpleFolderTest extends Thread {
  // constants for the state of the test.  Useful for deciding what
  // work needs to be performed next
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

  // constants for deciding the size of the test
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

  // References to the emails that were created.  Makes accessing
  // rotation easier to perform later on
  static public Vector addedEmails = new Vector();

  // store the results of the test
  public static StringBuffer buffer = new StringBuffer();
  
  /**
   * Constructor for the test.
   * @param name the name for the test thread
   */
  public SimpleFolderTest(String name) {
    super(name);
  }

  /**
   * The run method for a test thread.  Called to start the
   * execution of a test.
   */
  public void run() {
    // start the thread from at the appropriate start in the execution
    if (state == START) {
      buffer.append("Starting tests");
      startTest();
    }
    else if (state == SNAPSHOT_1) {
      buffer.append("Doing snapshots");
      testSnapShot1();
    }
    else if (state == ROTATE_A) {
      buffer.append("Rotating messages");
      rotateMessages();
    }
    else {
      buffer.append("deleting messages and folders");
      deleteMessages();
    }
    
    // check to see if we have gotten the right print outs
    if (buffer.toString().equals(theAnswer)) {
      System.out.println("Passed the SimpleFolderTest");
    } else {
      System.out.println("Failed the SimpleFolderTest");
      System.out.println(buffer.toString());
    }
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
	buffer.append("Could not create an EmailService to work with; Bailing.");
	return;
      }

      // JM just until we get some more continuations into the system
      try{
	buffer.append("Waiting...");
	Thread.sleep(3000);
      } catch (InterruptedException e) {
	buffer.append("INTERRUPTED: " + e);
      }

      // try to get the root Folder
      state = GOT_ROOT;
      Continuation command = new SimpleFolderCont();      
      _sender.getRootFolder(command);
    }

    else if (state == GOT_ROOT) {
      if (_rootFolder == null) {
	buffer.append("Root Folder was null after getRoot. Very bad, exitting.");
	return;
      }

      // once we have the name of the root, print it out
      buffer.append("Name of the root Folder: " + _rootFolder.getName());

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
	  buffer.append("Moving on to making subSubFolder " + currentSubFolder);
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
      buffer.append("Making subFolder " + subFolderState);
      Continuation command = new SimpleFolderCont();
      String newFolderName = folderName + " " +  (new Integer(subFolderState)).toString();
      subFolderState = subFolderState + 1;
      tempFolder.createChildFolder(newFolderName, command);
    }

    // otherwise, print result and move to next section
    else if (state == EMAIL_INIT) {
      // or if finished, move on to the next test
      buffer.append("Finished Creating the Folders");
      // print out the names of the subFolders
      String[] childNames = _rootFolder.getChildren();
      buffer.append("Number of sub folders is " + childNames.length);
      for (int i = 0; i < childNames.length; i++) {
	buffer.append(childNames[i]);
      }
      
      // print out the names of the subSubFolders
      for (int i = 0; i < subFolders.length; i++) {
	childNames = subFolders[i].getChildren();
	buffer.append("Number of subSub folders is " + childNames.length);
	for (int j = 0; j < childNames.length; j++) {
	  buffer.append(childNames[j]);
	}
      }
      testEmailStorage();
    }
    else {
      buffer.append("Invalid state in testFolderCreation");
    }
  }

  
  /**
   * Adds emails to the created Folders.
   */
  public void testEmailStorage() {
    // do initialization if necesary
    if (state == EMAIL_INIT) {
      buffer.append("Initializing the EmailStorage state");
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
	buffer.append("Adding email " + emailCount);

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
	buffer.append("Finished adding the emails to each Folder, moving on to Snapshot");
	state = SNAPSHOT_1;
	emailCount = 0;
	SimpleFolderTest nextTest = new SimpleFolderTest("SFT2");
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
      buffer.append("Adding snapshot email " + emailCount);
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
    buffer.append("Entering testEmails1. EmailCount is: " + emailCount + ", and state is:" + state);
    if (state == CHECK_EMAILS_1A) {
      // if we are done with the root folder
      if (emailCount > 0) {
	emailCount = 0;
	state = CHECK_EMAILS_1B;
	testEmails1();
      } else {
	buffer.append("\nRoot Folder contains the emails:" );
	_rootFolder.getMessages(new AddEmailCont());
      }
      
    } else if (state == CHECK_EMAILS_1B) {
      // if we are done with the subFolders, move on to the next part
      if (emailCount >= subFolders.length) {
	state = CHECK_EMAILS_1C;
	emailCount = 0;
	testEmails1();
      } else {
	buffer.append("\nSub Folder " + emailCount + "  contains the emails:" );
	subFolders[emailCount].getMessages(new AddEmailCont());
      }
      
    } else {
      // if we are done with the subSubFolders, move on to the next part
      if (emailCount >= subSubFolders.length) {
	state = CHECK_EVENTS_1A;
	emailCount = 0;
	buffer.append("Done with testEmails1, emailCount is " + emailCount + 
			   ", subFolder length is " + subFolders.length + 
			   ", and subSubFolder length is " + subSubFolders.length);	
	testEvents1();
      } else {
	buffer.append("\nSub Sub Folder " + emailCount + "  contains the emails:" );
	subSubFolders[emailCount].getMessages(new AddEmailCont());
      }
    }
  }

  /**
   * Checks that each of the Folders contains the appropriate event
   * log.
   */
  public void testEvents1() {
    buffer.append("Entering testEvents1. EmailCount is: " + emailCount + ", and state is:" + state);
    if (state == CHECK_EVENTS_1A) {
      // if we are done with the root folder
      if (emailCount > 0) {
	emailCount = 0;
	state = CHECK_EVENTS_1B;
	testEvents1();
      } else {
	buffer.append("\nRoot Folder contains the following events:" );
	_rootFolder.getCompleteEventLog(new AddEmailCont());
      }
      
    } else if (state == CHECK_EVENTS_1B) {
      // if we are done with the subFolders, move on to the next part
      if (emailCount >= subFolders.length) {
	state = CHECK_EVENTS_1C;
	emailCount = 0;
	testEvents1();
      } else {
	buffer.append("\nSub Folder " + emailCount + "  contains the events:" );
	subFolders[emailCount].getCompleteEventLog(new AddEmailCont());
      }
      
    } else {
      // if we are done with the subSubFolders, move on to the next part
      if (emailCount >= subSubFolders.length) {
	state = CHECK_FOLDERS;
	emailCount = 0;
	buffer.append("Done with testEvents1, emailCount is " + emailCount + 
			   ", subFolder length is " + subFolders.length + 
			   ", and subSubFolder length is " + subSubFolders.length);	
	testFolders();
      } else {
	buffer.append("\nSub Sub Folder " + emailCount + "  contains the events:" );
	subSubFolders[emailCount].getCompleteEventLog(new AddEmailCont());
      }
    }
  }

  /**
   * Helper method that prints an array of child folders.
   * @param children string array of child names
   */
  private void printChildFolders(String[] children) {
    for (int i = 0; i < children.length; i++) {
      buffer.append(children[i]);
    }
    buffer.append("");
  }

  /**
   * Prints out the child Folders that belong to each of the folders.
   */
  public void testFolders() {
    buffer.append("\n\nEntering testFolders!!! ");
    // check the root Folder
    buffer.append("RootFolder's child folders");
    printChildFolders(_rootFolder.getChildren());

    // check the subFolders
    buffer.append("subFolder's child folders");
    for (int i = 0; i < subFolders.length; i++) {
      printChildFolders(subFolders[i].getChildren());
    }

    // check the subSubFolders
    buffer.append("subSubFolder's child folders");
    for (int i = 0; i < subSubFolders.length; i++) {
      printChildFolders(subSubFolders[i].getChildren());
    }

    // start a new thread to continue the work.  This allows us to
    // reset the stack frame and avoid stack overflow errors    
    emailCount = 0;
    state = ROTATE_A;
    SimpleFolderTest nextTest = new SimpleFolderTest("SFT3");
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
    buffer.append("entering rotate messages. State: " + state + " emailCount: " + emailCount+"\n");

    // rotate messages among the subFolder's
    if (state == ROTATE_A) {
      buffer.append("rotating subFolder message " + emailCount);
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
	buffer.append("SourceEmail: " + sourceEmail + "  sourceFolder: " + sourceFolder + "  targetFolder: " + targetFolder);
	sourceFolder.moveMessage(sourceEmail, targetFolder, new AddEmailCont());
      }

    // rotate messages among the subSubFolder's
    } else {
      buffer.append("rotating subSubFolder message " + emailCount);
      // if we are done with the subSubFolder's, move on to the next test
      if (emailCount >= subSubFolders.length) {
	emailCount = 0;
	state = DELETE_1A;
	buffer.append("\n\n\n\n");
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
    buffer.append("Deleting messages.  State is " + state + "  emailCount is " + emailCount+"\n");
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
	buffer.append("Deleting message " + offset);	
	// remove the email
	subSubFolders[emailCount].removeMessage(targetEmail, new AddEmailCont());
      }

    // print the new contents of the subSubFolders      
    } else if (state == DELETE_1B) {      
      // if we are done with this part, move on to the next
      if (emailCount >= subSubFolders.length) {
	emailCount = 0;
	state = DELETE_1C;
	SimpleFolderTest nextTest = new SimpleFolderTest("SFT4");
	nextTest.start();
	try {
	  stop();
	  wait();
	} catch (Exception e) {
	}
      } else {
	buffer.append("Checking Folder " + emailCount);
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
	buffer.append("Removing Folder " + emailCount);	
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
	buffer.append("Deleting message " + offset);	
	// remove the email
	subFolders[emailCount].removeMessage(targetEmail, new AddEmailCont());
      }

    // print the new contents of the subFolders      
    } else if (state == DELETE_2B) {      
      // if we are done with this part, move on to the next
      if (emailCount >= subFolders.length) {
	emailCount = 0;
	state = DELETE_2C;
	SimpleFolderTest nextTest = new SimpleFolderTest("SFT5");
	nextTest.start();
	try {
	  stop();
	  wait();
	} catch (Exception e) {
	}
      } else {
	buffer.append("Checking Folder " + emailCount);
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
	buffer.append("Removing Folder " + emailCount);	
	_rootFolder.removeFolder(subFolders[emailCount].getName(), new AddEmailCont());
      }    
    } else {
      buffer.append("Done!");
    }
  }
    
  /**
   * Creates an email from the given Strings.
   * @param send the name of the sender
   * @param recip the name of the recipient
   * @param sub the subject of the email
   * @param bodyText the text of the message
   * @return the email created from the parameters
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
      buffer.append("SimpleFolderCont received result. State is " + state + " " + currentSubFolder + " " + subFolderState);
      buffer.append("Adding new Folder " + (Folder)o);

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
	buffer.append("invalid state in SimpleFolderCount: " + state);
      }
    }

    public void receiveException(Exception e) {
      e.printStackTrace();
      buffer.append("Exception " + e + "  occured while trying to get Folder for SimpleFolderTest.");
    }
  }

  /**
   * The main continuation for the test, used to print out any result
   * and to pass control back to the calling method.
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
	  buffer.append(results[i]);
	}
	testEmails1();
      }
      else if ((state == CHECK_EVENTS_1A) || (state == CHECK_EVENTS_1B) || (state == CHECK_EVENTS_1C)) {
	Object[] results = (Object[])o;
	for (int i = 0; i < results.length; i++) {
	  buffer.append(results[i]);
	}
	testEvents1();
      }
      else if ((state == ROTATE_A) || (state == ROTATE_B)) {
	buffer.append("rotated message " + emailCount);
	rotateMessages();
      }
      else if ((state == DELETE_1A) || (state == DELETE_1B) || (state == DELETE_1C) ||
	       (state == DELETE_2A) || (state == DELETE_2B) || (state == DELETE_2C) ||
	       (state == DELETE_3)) {
	buffer.append("delete message cont");
	if ((state == DELETE_1B) || (state == DELETE_2B)) {
	  Object[] results = (Object[])o;
	  for (int i = 0; i < results.length; i++) {
	    buffer.append(results[i]);
	  }
	}
	deleteMessages();
      }
      else {
	buffer.append("Invalid state: " + state);
      }
    }

    public void receiveException(Exception e) {
      e.printStackTrace();
      buffer.append("Exception " + e + "  occured while trying to get Folder for SimpleFolderTest.");
    }
  }


  /**
   * Main method, used to create and start the first thread of the
   * test.
   */
  public static void main(String[] args) throws InterruptedException {
    SimpleFolderTest test1 = new SimpleFolderTest("SimpleTest1");
    test1.start();
    Thread.sleep(4000);
  }
  
  public static String theAnswer = "";
  /**
"Starting tests
PostLog lookup for user user1 failed.
Waiting...
Starting to get root Folder
Fetched the initial emailLogRef
Email Root Log did not exist, adding one
Starting a new ESAddRootFolderCont
ESAddRootFolderCont received a result.
Result is: rice.post.log.LogReference@bb0d0d
Starting a new ESRootFolderCont
ESRootFolderCont received a result.
Result is: rice.post.log.Log@958bb8
SimpleFolderCont received result. State is 10 0 0
Adding new Folder Root
Name of the root Folder: Root
Making subFolder 0
SimpleFolderCont received result. State is 20 0 1
Adding new Folder Folder 0
Making subFolder 1
SimpleFolderCont received result. State is 20 0 2
Adding new Folder Folder 1
Moving on to making subSubFolder 0
Making subFolder 0
SimpleFolderCont received result. State is 20 1 1
Adding new Folder Folder 1 0
Making subFolder 1
SimpleFolderCont received result. State is 20 1 2
Adding new Folder Folder 1 1
Moving on to making subSubFolder 1
Making subFolder 0
SimpleFolderCont received result. State is 20 2 1
Adding new Folder Folder 2 0
Making subFolder 1
SimpleFolderCont received result. State is 20 2 2
Adding new Folder Folder 2 1
Finished Creating the Folders
Number of sub folders is 2
Folder 1
Folder 0
Number of subSub folders is 2
Folder 1 0
Folder 1 1
Number of subSub folders is 2
Folder 2 1
Folder 2 0
Initializing the EmailStorage state
Adding email 0
Adding email 1
Adding email 2
Adding email 3
Adding email 4
Adding email 5
Adding email 6
Finished adding the emails to each Folder, moving on to Snapshot
Doing snapshots
Adding snapshot email 0
Adding snapshot email 1
Entering testEmails1. EmailCount is: 0, and state is:60

Root Folder contains the emails:
Subject: First Email for Folder 0
Send To: TestReciever 0
Entering testEmails1. EmailCount is: 1, and state is:60
Entering testEmails1. EmailCount is: 0, and state is:63

Sub Folder 0  contains the emails:
Subject: SS message 1
Send To: SS-Receiver 1
Subject: SS message 0
Send To: SS-Receiver 0
Subject: First Email for Folder 1
Send To: TestReciever 1
Entering testEmails1. EmailCount is: 1, and state is:63

Sub Folder 1  contains the emails:
Subject: First Email for Folder 2
Send To: TestReciever 2
Entering testEmails1. EmailCount is: 2, and state is:63
Entering testEmails1. EmailCount is: 0, and state is:66

Sub Sub Folder 0  contains the emails:
Subject: First Email for Folder 3
Send To: TestReciever 3
Entering testEmails1. EmailCount is: 1, and state is:66

Sub Sub Folder 1  contains the emails:
Subject: First Email for Folder 4
Send To: TestReciever 4
Entering testEmails1. EmailCount is: 2, and state is:66

Sub Sub Folder 2  contains the emails:
Subject: First Email for Folder 5
Send To: TestReciever 5
Entering testEmails1. EmailCount is: 3, and state is:66

Sub Sub Folder 3  contains the emails:
Subject: First Email for Folder 6
Send To: TestReciever 6
Entering testEmails1. EmailCount is: 4, and state is:66
Done with testEmails1, emailCount is 0, subFolder length is 2, and subSubFolder length is 4
Entering testEvents1. EmailCount is: 0, and state is:70

Root Folder contains the following events:
Insert Email Event for: First Email for Folder 0
Insert Folder Event for: Folder 1
Insert Folder Event for: Folder 0
Entering testEvents1. EmailCount is: 1, and state is:70
Entering testEvents1. EmailCount is: 0, and state is:73

Sub Folder 0  contains the events:
Insert Email Event for: SS message 1
Insert Email Event for: SS message 0
Insert Email Event for: First Email for Folder 1
Insert Folder Event for: Folder 1 1
Insert Folder Event for: Folder 1 0
Entering testEvents1. EmailCount is: 1, and state is:73

Sub Folder 1  contains the events:
Insert Email Event for: First Email for Folder 2
Insert Folder Event for: Folder 2 1
Insert Folder Event for: Folder 2 0
Entering testEvents1. EmailCount is: 2, and state is:73
Entering testEvents1. EmailCount is: 0, and state is:76

Sub Sub Folder 0  contains the events:
Insert Email Event for: First Email for Folder 3
Entering testEvents1. EmailCount is: 1, and state is:76

Sub Sub Folder 1  contains the events:
Insert Email Event for: First Email for Folder 4
Entering testEvents1. EmailCount is: 2, and state is:76

Sub Sub Folder 2  contains the events:
Insert Email Event for: First Email for Folder 5
Entering testEvents1. EmailCount is: 3, and state is:76

Sub Sub Folder 3  contains the events:
Insert Email Event for: First Email for Folder 6
Entering testEvents1. EmailCount is: 4, and state is:76
Done with testEvents1, emailCount is 0, subFolder length is 2, and subSubFolder length is 4


Entering testFolders!!! 
RootFolder's child folders
Folder 1
Folder 0

subFolder's child folders
Folder 1 0
Folder 1 1

Folder 2 1
Folder 2 0

subSubFolder's child folders




Rotating messages
entering rotate messages. State: 90 emailCount: 0

rotating subFolder message 0
SourceEmail: Subject: First Email for Folder 1
Send To: TestReciever 1  sourceFolder: Folder 0  targetFolder: Folder 1
rotated message 1
entering rotate messages. State: 90 emailCount: 1

rotating subFolder message 1
SourceEmail: Subject: First Email for Folder 2
Send To: TestReciever 2  sourceFolder: Folder 1  targetFolder: Folder 0
rotated message 2
entering rotate messages. State: 90 emailCount: 2

rotating subFolder message 2
entering rotate messages. State: 95 emailCount: 0

rotating subSubFolder message 0
rotated message 1
entering rotate messages. State: 95 emailCount: 1

rotating subSubFolder message 1
rotated message 2
entering rotate messages. State: 95 emailCount: 2

rotating subSubFolder message 2
rotated message 3
entering rotate messages. State: 95 emailCount: 3

rotating subSubFolder message 3
rotated message 4
entering rotate messages. State: 95 emailCount: 4

rotating subSubFolder message 4





Deleting messages.  State is 100  emailCount is 0
Deleting message 3
delete message cont
Deleting messages.  State is 100  emailCount is 1
Deleting message 0
delete message cont
Deleting messages.  State is 100  emailCount is 2
Deleting message 1
delete message cont
Deleting messages.  State is 100  emailCount is 3
Deleting message 2
delete message cont
Deleting messages.  State is 100  emailCount is 4
Deleting messages.  State is 103  emailCount is 0
Checking Folder 0
delete message cont
Delete Email Event for: First Email for Folder 6
Insert Email Event for: First Email for Folder 6
Delete Email Event for: First Email for Folder 3
Insert Email Event for: First Email for Folder 3
Deleting messages.  State is 103  emailCount is 1
Checking Folder 1
delete message cont
Delete Email Event for: First Email for Folder 3
Delete Email Event for: First Email for Folder 4
Insert Email Event for: First Email for Folder 3
Insert Email Event for: First Email for Folder 4
Deleting messages.  State is 103  emailCount is 2
Checking Folder 2
delete message cont
Delete Email Event for: First Email for Folder 4
Delete Email Event for: First Email for Folder 5
Insert Email Event for: First Email for Folder 4
Insert Email Event for: First Email for Folder 5
Deleting messages.  State is 103  emailCount is 3
Checking Folder 3
delete message cont
Delete Email Event for: First Email for Folder 5
Delete Email Event for: First Email for Folder 6
Insert Email Event for: First Email for Folder 5
Insert Email Event for: First Email for Folder 6
Deleting messages.  State is 103  emailCount is 4
deleting messages and folders
Deleting messages.  State is 106  emailCount is 0
Removing Folder 0
delete message cont
Deleting messages.  State is 106  emailCount is 1
Removing Folder 1
delete message cont
Deleting messages.  State is 106  emailCount is 2
Removing Folder 2
delete message cont
Deleting messages.  State is 106  emailCount is 3
Removing Folder 3
delete message cont
Deleting messages.  State is 106  emailCount is 4
Deleting messages.  State is 110  emailCount is 0
Deleting message 1
delete message cont
Deleting messages.  State is 110  emailCount is 1
Deleting message 0
delete message cont
Deleting messages.  State is 110  emailCount is 2
Deleting messages.  State is 113  emailCount is 0
Checking Folder 0
delete message cont
Delete Email Event for: First Email for Folder 2
Delete Folder Event for: Folder 1 1
Delete Folder Event for: Folder 1 0
Insert Email Event for: First Email for Folder 2
Delete Email Event for: First Email for Folder 1
Insert Email Event for: SS message 1
Insert Email Event for: SS message 0
Insert Email Event for: First Email for Folder 1
Insert Folder Event for: Folder 1 1
Insert Folder Event for: Folder 1 0
Deleting messages.  State is 113  emailCount is 1
Checking Folder 1
delete message cont
Delete Email Event for: First Email for Folder 1
Delete Folder Event for: Folder 2 1
Delete Folder Event for: Folder 2 0
Delete Email Event for: First Email for Folder 2
Insert Email Event for: First Email for Folder 1
Insert Email Event for: First Email for Folder 2
Insert Folder Event for: Folder 2 1
Insert Folder Event for: Folder 2 0
Deleting messages.  State is 113  emailCount is 2
deleting messages and folders
Deleting messages.  State is 116  emailCount is 0
Removing Folder 0
delete message cont
Deleting messages.  State is 116  emailCount is 1
Removing Folder 1
delete message cont
Deleting messages.  State is 116  emailCount is 2
Deleting messages.  State is 120  emailCount is 0
Done!
Finished with Folder Tests";
  */
}
