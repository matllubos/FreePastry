package rice.email.test;

import rice.*;
import rice.email.*;
import rice.post.*;
import java.util.*;

public class SimpleFolderTest extends EmailTest {
  public static final int START = 0;
  public static final int GOT_ROOT = 10;

  // the sender
  private EmailService _sender;
  // the root folder for the sender
  private Folder _rootFolder;
  // the current state of the program
  private int _state;

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
      services = this.createEmailServices(new String[]{"user1"});		    
      _sender = services[0];
      if (_sender == null) {
	System.out.println("Could not create an EmailService to work with; Bailing.");
	exit(1);
      }
      // get the root
      _state = GOT_ROOT;
      Continuation command = new GetFolderCont(_rootFolder);      
      _sender.getRootFolder(command);
    }
    else if (_state == GOT_ROOT) {
      System.out.println("Name of the root Folder: " + _rootFolder.getName());
    }
  }


    /*
      // create an email to send
      String subject = "Hello World";
      String bodyText = "Hello World!";
		
      EmailData body = new EmailData(bodyText.getBytes());
      EmailData[] attachments = new EmailData[0];

      PostEntityAddress[] recipients = new PostEntityAddress[1];
      recipients[0] = receiver.getPost().getEntityAddress();

      Email email = new Email((PostUserAddress) sender.getPost().getEntityAddress(),
      recipients, subject,
      body, attachments);

      try {
      sender.sendMessage(email, null);
      } catch(Exception e) {
      System.err.println("Error sending email");
      e.printStackTrace();
      }
    */

  // Gets the root Folder, continues on with test
  protected class GetFolderCont implements Continuation {
    Folder _folder;
    public GetFolderCont(Folder folder) {
      _folder = folder;      
    }
    public void start() {}
    public void receiveResult(Object o) {      
      _folder = (Folder)o;
      testBody();
    }
    public void receiveException(Exception e) {
      System.out.println("Exception " + e + "  occured while trying to get Folder");
    }
  }

  public static void main(String[] args) throws InterruptedException {

    SimpleFolderTest set = new SimpleFolderTest();

    set.startTest();

    Thread.sleep(5000);
  }
}
