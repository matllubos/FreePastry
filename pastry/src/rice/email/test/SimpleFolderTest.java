package rice.email.test;

import rice.*;
import rice.email.*;
import rice.post.*;
import java.util.*;

public class SimpleFolderTest extends EmailTest {
  public static final int START = 0;
  public static final int GOT_ROOT = 10;
  public static final int MADE_CHILD_1 = 20;
  public static final int MADE_CHILD_2 = 30;

  // the sender
  private EmailService _sender;
  // the root folder for the sender
  private Folder _rootFolder;
  // the current state of the program
  private int _state;

  // the immediate subFolders
  Folder _subFolder1, _subFolder2;

  // methods
  public void startTest() {
    // set the initial state
    _state = START;
    testBody();
  }

  public void testBody() {
    if (_state == START) {
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
	Thread.sleep(5000);
      } catch (InterruptedException e) {
	System.out.println("INTERRUPTED: " + e);
      }

      // try to get the root
      _state = GOT_ROOT;
      Continuation command = new SimpleFolderCont(_rootFolder);      
      _sender.getRootFolder(command);
      // if this does not work, make a root for the EmailApp
      

      // now get the root
      

    }

    else if (_state == GOT_ROOT) {
      // once we have the name of the root, print it out
      System.out.println("Name of the root Folder: " + _rootFolder.getName());

      // create a new Folder under the root
      _state = MADE_CHILD_1;
      Continuation command = new SimpleFolderCont(_subFolder1);
      _rootFolder.createChildFolder("Adam", command);
    }
    else if (_state == MADE_CHILD_1) {
      // print out the info for the child folders
      System.out.println("Reached make child 1");
      String[] childNames = _rootFolder.getChildren();
      System.out.println("Number of child folders is " + childNames.length);
      for (int i = 0; i < childNames.length; i++) {
	System.out.println(childNames[i]);
      }
      // create a new Folder under the root
      _state = MADE_CHILD_2;
      Continuation command = new SimpleFolderCont(_subFolder2);
      _rootFolder.createChildFolder("Bob", command);
    }
    else if (_state == MADE_CHILD_2) {
      System.out.println("Reached make child 1");
      // print out the info for the child folders
      System.out.println("Reached make child 1");
      String[] childNames = _rootFolder.getChildren();
      System.out.println("Number of child folders is " + childNames.length);
      for (int i = 0; i < childNames.length; i++) {
	System.out.println(childNames[i]);
      }
      // do the next step
    }
  }

  // Gets the root Folder, continues on with test
  protected class SimpleFolderCont implements Continuation {
    Folder _folder;
    public SimpleFolderCont(Folder folder) {
      _folder = folder;      
    }
    public void start() {}
    public void receiveResult(Object o) {      
      _folder = (Folder)o;
      testBody();
    }
    public void receiveException(Exception e) {
      e.printStackTrace();
      System.out.println("Exception " + e + "  occured while trying to get Folder for SimpleFolderTest.");
    }
  }

  public static void main(String[] args) throws InterruptedException {

    SimpleFolderTest set = new SimpleFolderTest();

    set.startTest();

    Thread.sleep(5000);
  }
}
