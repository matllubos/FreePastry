package rice.email.test;

import rice.*;
import rice.email.*;
import rice.post.*;
import java.util.*;

public class SimpleFolderTest extends EmailTest {
  public static final int START = 0;
  public static final int GOT_ROOT = 10;
  public static final int MAKING_SUBS = 20;
  public static final int EMAIL_INIT = 30;
  public static final int EMAIL_ADD = 40;
  public static final int SNAPSHOT_1 = 50;
  public static final int CHECK_1 = 60;
  public static final int MAX_SUB_FOLDERS = 2;
  public static final int MAX_SUB_SUB_FOLDERS = MAX_SUB_FOLDERS * MAX_SUB_FOLDERS;

  // the sender
  private EmailService _sender;
  // the root folder for the sender
  private Folder _rootFolder;
  // the current state of the program
  private int state;

  // counters for the state of the Folder creation
  int subFolderState;
  int currentSubFolder;

  // the subFolders
  Folder[] subFolders;

  // the subSubFolders
  Folder[] subSubFolders;

  // temp Folder for all sorts of uses
  Folder tempFolder;

  // base name for Folder creation
  String folderName;

  // state for adding emails to the Folders
  Vector targets;
  int emailCount;

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
      services = this.createEmailServices(new String[]{"user1"}, 2000);		    
      _sender = services[0];
      if (_sender == null) {
	System.out.println("Could not create an EmailService to work with; Bailing.");
	return;
      }

      // JM just until we get some more continuations into the system
      try{
	System.out.println("Waiting...");
	Thread.sleep(4000);
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
      folderName = "Folder";
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
	  folderName = "Folder" + (new Integer(currentSubFolder)).toString();
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
      String newFolderName = folderName + (new Integer(subFolderState)).toString();
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
	Email newEmail = makeEmail("TestSender " + postFix, "TestReciever " + postFix, "First Email for Folder" + postFix, "");
	Continuation cont = new AddEmailCont();
	((Folder)(targets.get(emailCount))).addMessage(newEmail, cont);
      }
      // otherwise change the state and go on to the next test
      else {
	System.out.println("Finished adding the emails to each Folder, moving on to Snapshot");
	state = SNAPSHOT_1;
	emailCount = 0;
	testSnapShot1();
      }
    }
  }

  /**
   * Adds emails to a Folder until a snapShot node is created.
   */
  public void testSnapShot1() {
    // if we have not yet added enough emails to make a new SnapShot
    if (emailCount < Folder.COMPRESS_LIMIT+1) {
      // add another email.
      String postFix = (new Integer(emailCount)).toString();
      Email newEmail = makeEmail("SS-Sender " + postFix, "SS-Receiver " + postFix, "SS message" + postFix, "Make me a snapshot!");
      Continuation cont = new AddEmailCont();
      subFolders[0].addMessage(newEmail, cont);
    }
    // otherwise we are done, and ready to move to the next test
    else {
      state = CHECK_1;
      checkFirstTest();
    }
  }

  /**
   * Does stuff.
   */
  public void checkFirstTest() {
    return;
  }



  // Gets the root Folder, continues on with test
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
      System.out.println("AddEmailCont received result.");
      emailCount = emailCount + 1;
      if (state == EMAIL_ADD) {
	testEmailStorage();
      }
      else if (state == SNAPSHOT_1) {
	testSnapShot1();
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

    SimpleFolderTest set = new SimpleFolderTest();

    set.startTest();

    Thread.sleep(4000);
  }
}
