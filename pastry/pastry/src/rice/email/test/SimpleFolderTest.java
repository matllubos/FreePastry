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
  public static final int DONE  = 120;  

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
      buffer.append("Starting tests\n");
      startTest();
    }
    else if (state == SNAPSHOT_1) {
      buffer.append("Starting snapshots\n");
      testSnapShot1();
    }
    else if (state == ROTATE_A) {
      buffer.append("Staring to Rotate messages\n");
      rotateMessages();
    }
    else if ((state == DELETE_1C) || (state == DELETE_2C)) {
      buffer.append("Deleting messages and folders\n");
      deleteMessages();
    } else if (state == DONE) {
      buffer.append("Done!\n");
      
      // get the output string, and remove the eol's from it.
      String original = buffer.toString();
      String result = buffer.toString();
      int base = 0;
      int target = result.indexOf('\n', base);
      int offset = 0;
      while (target >= 0) {
	//System.out.println("Target: " + target + " base: " + base + " offset: " + offset);
	buffer.deleteCharAt(target - offset);
	base = target+1;
	offset += 1;
	target = result.indexOf('\n', base);
      }
      result = buffer.toString();
      
      // check to see if we have gotten the right print outs
      if (result.equals(theAnswer)) {
	System.out.println("Passed the SimpleFolderTest");
      } else {
	System.out.println("Failed the SimpleFolderTest");
	System.out.println(original);
      }
    }
    else {
      buffer.append("Error, invalid state " + state + " when starting thread\n");
    }
    return;
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
	buffer.append("Error: Root Folder was null after the call to getRoot(). Very bad, exitting.");
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
	  buffer.append("Creating subSubFolder " + currentSubFolder + "\n");
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
      buffer.append("Creating subFolder " + subFolderState + "\n");
      Continuation command = new SimpleFolderCont();
      String newFolderName = folderName + " " +  (new Integer(subFolderState)).toString();
      subFolderState = subFolderState + 1;
      tempFolder.createChildFolder(newFolderName, command);
    }

    // otherwise, print result and move to next section
    else if (state == EMAIL_INIT) {
      // print out the names of the subFolders
      String[] childNames = _rootFolder.getChildren();
      //buffer.append("Number of sub folders is " + childNames.length);
      //for (int i = 0; i < childNames.length; i++) {
      //buffer.append(childNames[i] + "\n");
      //}
      
      // print out the names of the subSubFolders
      for (int i = 0; i < subFolders.length; i++) {
	childNames = subFolders[i].getChildren();
	//buffer.append("Number of subSub folders is " + childNames.length);
	//for (int j = 0; j < childNames.length; j++) {
	//buffer.append(childNames[j] + "\n");
	//}
      }
      testEmailStorage();
    }
    else {
      buffer.append("Invalid state in testFolderCreation\n");
    }
  }

  
  /**
   * Adds emails to the created Folders.
   */
  public void testEmailStorage() {
    // do initialization if necesary
    if (state == EMAIL_INIT) {
      //buffer.append("Initializing the EmailStorage state");
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
	//buffer.append("Adding email " + emailCount);

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
	//buffer.append("Finished adding the emails to each Folder, moving on to Snapshot");
	spawnChild(SNAPSHOT_1, 0);	
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
      buffer.append("Adding snapshot email " + emailCount + "\n");
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
    //buffer.append("Entering testEmails1. EmailCount is: " + emailCount + ", and state is:" + state);
    if (state == CHECK_EMAILS_1A) {
      // if we are done with the root folder
      if (emailCount > 0) {
	emailCount = 0;
	state = CHECK_EMAILS_1B;
	testEmails1();
      } else {
	buffer.append("\nRoot Folder contains the emails:\n" );
	_rootFolder.getMessages(new AddEmailCont());
      }
      
    } else if (state == CHECK_EMAILS_1B) {
      // if we are done with the subFolders, move on to the next part
      if (emailCount >= subFolders.length) {
	state = CHECK_EMAILS_1C;
	emailCount = 0;
	testEmails1();
      } else {
	buffer.append("\nSub Folder " + emailCount + "  contains the emails:\n" );
	subFolders[emailCount].getMessages(new AddEmailCont());
      }
      
    } else {
      // if we are done with the subSubFolders, move on to the next part
      if (emailCount >= subSubFolders.length) {
	state = CHECK_EVENTS_1A;
	emailCount = 0;
	//buffer.append("Done with testEmails1, emailCount is " + emailCount + 
	//	   ", subFolder length is " + subFolders.length + 
	//	   ", and subSubFolder length is " + subSubFolders.length);	
	testEvents1();
      } else {
	buffer.append("\nSub Sub Folder " + emailCount + "  contains the emails:\n" );
	subSubFolders[emailCount].getMessages(new AddEmailCont());
      }
    }
  }

  /**
   * Checks that each of the Folders contains the appropriate event
   * log.
   */
  public void testEvents1() {
    //buffer.append("Entering testEvents1. EmailCount is: " + emailCount + ", and state is:" + state);
    if (state == CHECK_EVENTS_1A) {
      // if we are done with the root folder
      if (emailCount > 0) {
	emailCount = 0;
	state = CHECK_EVENTS_1B;
	testEvents1();
      } else {
	buffer.append("\nRoot Folder contains the following events:\n" );
	_rootFolder.getCompleteEventLog(new AddEmailCont());
      }
      
    } else if (state == CHECK_EVENTS_1B) {
      // if we are done with the subFolders, move on to the next part
      if (emailCount >= subFolders.length) {
	state = CHECK_EVENTS_1C;
	emailCount = 0;
	testEvents1();
      } else {
	buffer.append("\nSub Folder " + emailCount + "  contains the events:\n" );
	subFolders[emailCount].getCompleteEventLog(new AddEmailCont());
      }
      
    } else {
      // if we are done with the subSubFolders, move on to the next part
      if (emailCount >= subSubFolders.length) {
	state = CHECK_FOLDERS;
	emailCount = 0;
	//buffer.append("Done with testEvents1, emailCount is " + emailCount + 
	//		   ", subFolder length is " + subFolders.length + 
	//		   ", and subSubFolder length is " + subSubFolders.length);	
	testFolders();
      } else {
	buffer.append("\nSub Sub Folder " + emailCount + "  contains the events:\n" );
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
      buffer.append(children[i] + "\n");
    }
    buffer.append("\n");
  }

  /**
   * Prints out the child Folders that belong to each of the folders.
   */
  public void testFolders() {
    //buffer.append("\n\nEntering testFolders!!! ");
    // check the root Folder
    buffer.append("RootFolder's child folders\n");
    printChildFolders(_rootFolder.getChildren());

    // check the subFolders
    buffer.append("subFolder's child folders\n");
    for (int i = 0; i < subFolders.length; i++) {
      printChildFolders(subFolders[i].getChildren());
    }

    // check the subSubFolders
    buffer.append("subSubFolder's child folders\n");
    for (int i = 0; i < subSubFolders.length; i++) {
      printChildFolders(subSubFolders[i].getChildren());
    }

    // start a new thread to continue the work.  This allows us to
    // reset the stack frame and avoid stack overflow errors    
    spawnChild(ROTATE_A, 0);
  }

  /**
   * Rotates messages between the Folders.  Basically moves each message to the next Folder
   * at its level in the tree.
   */
  public void rotateMessages() {
    //buffer.append("entering rotate messages. State: " + state + " emailCount: " + emailCount+"\n");

    // rotate messages among the subFolder's
    if (state == ROTATE_A) {
      //buffer.append("rotating subFolder message " + emailCount);
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
	//buffer.append("SourceEmail: " + sourceEmail + "  sourceFolder: " + sourceFolder + "  targetFolder: " + targetFolder);
	sourceFolder.moveMessage(sourceEmail, targetFolder, new AddEmailCont());
      }

    // rotate messages among the subSubFolder's
    } else {
      //buffer.append("rotating subSubFolder message " + emailCount);
      // if we are done with the subSubFolder's, move on to the next test
      if (emailCount >= subSubFolders.length) {
	emailCount = 0;
	state = DELETE_1A;
	//buffer.append("\n\n\n\n");
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
    //buffer.append("Deleting messages.  State is " + state + "  emailCount is " + emailCount+"\n");
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
	buffer.append("Deleting message " + offset + "\n");	
	// remove the email
	subSubFolders[emailCount].removeMessage(targetEmail, new AddEmailCont());
      }

    // print the new contents of the subSubFolders      
    } else if (state == DELETE_1B) {      
      // if we are done with this part, move on to the next
      if (emailCount >= subSubFolders.length) {
	spawnChild(DELETE_1C, 0);
      } else {
	buffer.append("Checking Folder " + emailCount + "\n");
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
	buffer.append("Removing Folder " + emailCount + "\n");	
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
	buffer.append("Deleting message " + offset + "\n");	
	// remove the email
	subFolders[emailCount].removeMessage(targetEmail, new AddEmailCont());
      }

    // print the new contents of the subFolders      
    } else if (state == DELETE_2B) {      
      // if we are done with this part, move on to the next
      if (emailCount >= subFolders.length) {
	spawnChild(DELETE_2C, 0);
      } else {
	buffer.append("Checking Folder " + emailCount + "\n");
	subFolders[emailCount].getCompleteEventLog(new AddEmailCont());
      }
    // then remove the subFolders themselves    
    } else if (state == DELETE_2C) {      
      // if we are done with this part, move on to the next
      if (emailCount >= subFolders.length) {
	spawnChild(DONE, 0);
      } else {
	buffer.append("Removing Folder " + emailCount + "\n");	
	_rootFolder.removeFolder(subFolders[emailCount].getName(), new AddEmailCont());
      }    
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
   * Creates a new child thread to continue on the work.  Is used so
   * that the stack will be reset and not overflow.  Note that this
   * should not be called if the current thread has any more work to
   * do, i.e. this call should be the current thread's last act.
   * @param newState the state for the newThread to start in
   * @param newEmailCount the value that emailCount should start at
   */
  private void spawnChild(int newState, int newEmailCount) {
    state = newState;
    emailCount = newEmailCount;
    SimpleFolderTest nextTest = new SimpleFolderTest("SFT");
    nextTest.start();
    return;
  }
  
  
  /**
   * Gets the RootFolder and subFolders.
   */
  protected class SimpleFolderCont implements Continuation {
    public SimpleFolderCont() {}
    public void start() {}
    public void receiveResult(Object o) {      
      //buffer.append("SimpleFolderCont received result. State is " + state + " " + currentSubFolder + " " + subFolderState);
      //buffer.append("Adding new Folder " + (Folder)o);

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
	buffer.append("invalid state in SimpleFolderCount: " + state + "\n");
      }
    }

    public void receiveException(Exception e) {
      e.printStackTrace();
      buffer.append("Exception " + e + "  occured while trying to get Folder for SimpleFolderTest.\n");
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
	  buffer.append(results[i] + "\n");
	}
	testEmails1();
      }
      else if ((state == CHECK_EVENTS_1A) || (state == CHECK_EVENTS_1B) || (state == CHECK_EVENTS_1C)) {
	Object[] results = (Object[])o;
	for (int i = 0; i < results.length; i++) {
	  buffer.append(results[i] + "\n");
	}
	testEvents1();
      }
      else if ((state == ROTATE_A) || (state == ROTATE_B)) {
	//buffer.append("rotated message " + emailCount);
	rotateMessages();
      }
      else if ((state == DELETE_1A) || (state == DELETE_1B) || (state == DELETE_1C) ||
	       (state == DELETE_2A) || (state == DELETE_2B) || (state == DELETE_2C) ||
	       (state == DONE)) {
	//buffer.append("delete message cont");
	if ((state == DELETE_1B) || (state == DELETE_2B)) {
	  Object[] results = (Object[])o;
	  for (int i = 0; i < results.length; i++) {
	    buffer.append(results[i] + "\n");
	  }
	}
	deleteMessages();
      }
      else {
	buffer.append("Invalid state: " + state + "\n");
      }
    }

    public void receiveException(Exception e) {
      e.printStackTrace();
      buffer.append("Exception " + e + "  occured while trying to get Folder for SimpleFolderTest.\n");
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
  
  public static String theAnswer = "Starting testsName of the root Folder: RootCreating subFolder 0Creating subFolder 1Creating subSubFolder 0Creating subFolder 0Creating subFolder 1Creating subSubFolder 1Creating subFolder 0Creating subFolder 1Starting snapshotsAdding snapshot email 0Adding snapshot email 1Root Folder contains the emails:Subject: First Email for Folder 0Send To: TestReciever 0Sub Folder 0  contains the emails:Subject: SS message 1Send To: SS-Receiver 1Subject: SS message 0Send To: SS-Receiver 0Subject: First Email for Folder 1Send To: TestReciever 1Sub Folder 1  contains the emails:Subject: First Email for Folder 2Send To: TestReciever 2Sub Sub Folder 0  contains the emails:Subject: First Email for Folder 3Send To: TestReciever 3Sub Sub Folder 1  contains the emails:Subject: First Email for Folder 4Send To: TestReciever 4Sub Sub Folder 2  contains the emails:Subject: First Email for Folder 5Send To: TestReciever 5Sub Sub Folder 3  contains the emails:Subject: First Email for Folder 6Send To: TestReciever 6Root Folder contains the following events:Insert Email Event for: First Email for Folder 0Insert Folder Event for: Folder 1Insert Folder Event for: Folder 0Sub Folder 0  contains the events:Insert Email Event for: SS message 1Insert Email Event for: SS message 0Insert Email Event for: First Email for Folder 1Insert Folder Event for: Folder 1 1Insert Folder Event for: Folder 1 0Sub Folder 1  contains the events:Insert Email Event for: First Email for Folder 2Insert Folder Event for: Folder 2 1Insert Folder Event for: Folder 2 0Sub Sub Folder 0  contains the events:Insert Email Event for: First Email for Folder 3Sub Sub Folder 1  contains the events:Insert Email Event for: First Email for Folder 4Sub Sub Folder 2  contains the events:Insert Email Event for: First Email for Folder 5Sub Sub Folder 3  contains the events:Insert Email Event for: First Email for Folder 6RootFolder's child foldersFolder 1Folder 0subFolder's child foldersFolder 1 0Folder 1 1Folder 2 1Folder 2 0subSubFolder's child foldersStaring to Rotate messagesDeleting message 3Deleting message 0Deleting message 1Deleting message 2Checking Folder 0Delete Email Event for: First Email for Folder 6Insert Email Event for: First Email for Folder 6Delete Email Event for: First Email for Folder 3Insert Email Event for: First Email for Folder 3Checking Folder 1Delete Email Event for: First Email for Folder 3Delete Email Event for: First Email for Folder 4Insert Email Event for: First Email for Folder 3Insert Email Event for: First Email for Folder 4Checking Folder 2Delete Email Event for: First Email for Folder 4Delete Email Event for: First Email for Folder 5Insert Email Event for: First Email for Folder 4Insert Email Event for: First Email for Folder 5Checking Folder 3Delete Email Event for: First Email for Folder 5Delete Email Event for: First Email for Folder 6Insert Email Event for: First Email for Folder 5Insert Email Event for: First Email for Folder 6Deleting messages and foldersRemoving Folder 0Removing Folder 1Removing Folder 2Removing Folder 3Deleting message 1Deleting message 0Checking Folder 0Delete Email Event for: First Email for Folder 2Delete Folder Event for: Folder 1 1Delete Folder Event for: Folder 1 0Insert Email Event for: First Email for Folder 2Delete Email Event for: First Email for Folder 1Insert Email Event for: SS message 1Insert Email Event for: SS message 0Insert Email Event for: First Email for Folder 1Insert Folder Event for: Folder 1 1Insert Folder Event for: Folder 1 0Checking Folder 1Delete Email Event for: First Email for Folder 1Delete Folder Event for: Folder 2 1Delete Folder Event for: Folder 2 0Delete Email Event for: First Email for Folder 2Insert Email Event for: First Email for Folder 1Insert Email Event for: First Email for Folder 2Insert Folder Event for: Folder 2 1Insert Folder Event for: Folder 2 0Deleting messages and foldersRemoving Folder 0Removing Folder 1Done!";
}
