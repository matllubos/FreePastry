package rice.email.test;

import rice.*;
import rice.email.*;
import rice.email.messaging.*;
import rice.post.*;
import java.util.*;

/**
 * This test tests the core email sending/receiving functionality.
 * 
 * @author Derek Ruths, Joe Montgomery
 */
public class SendEmailTest extends EmailTest {

    public static final String EMAIL_SUBJECT = "Subject";
    public static final String EMAIL_BODY = "Body";
    public static final String EMAIL_ATT1 = "Attachment1";
    public static final String EMAIL_ATT2 = "Attachment2";
    
    // inner classes
    private class BodyChecker implements Continuation {

	public void receiveResult(Object result) {

	    if(result instanceof EmailData) {
		EmailData data = (EmailData) result;

		String body = new String(data.getData());
		
		if(!body.equals(EMAIL_BODY)) {
		    System.err.println("Email body wrong");
		    System.err.println("\t" +
				       body + " <> " + EMAIL_BODY);
		}
	    }
	}
	
	public void receiveException(Exception result) {
	    System.err.println("Error retrieving Email Body");
	}
    }

    private class AttachmentsChecker implements Continuation {

	public void receiveResult(Object result) {

	    if(result instanceof EmailData[]) {
		EmailData[] data = (EmailData[]) result;

		String attachment1 = new String(data[0].getData());
		String attachment2 = new String(data[1].getData());
		
		if(!attachment1.equals(EMAIL_ATT1)) {
		    System.err.println("Email attachment1 wrong");
		    System.err.println("\t" +
				       attachment1 + " <> " + EMAIL_ATT1);
		}

		if(!attachment2.equals(EMAIL_ATT2)) {
		    System.err.println("Email attachment2 wrong");
		    System.err.println("\t" +
				       attachment2 + " <> " + EMAIL_ATT2);
		}
	    }
	}

	public void receiveException(Exception result) {
	    System.err.println("Error retrieving Email Body");
	}

    }
    
	private class EmailReceiver implements Observer {
		public void update(Observable src, Object args) {
			System.out.println("SRC: " + src + ", ARG: " + args);

			if(args instanceof EmailNotificationMessage) {
			    Email email = ((EmailNotificationMessage) args).getEmail();

			    if(!email.getSubject().equals(EMAIL_SUBJECT)) {
				System.err.println("Email subject wrong");
				System.err.println("\t" +
						   email.getSubject()
						   + " <> " + EMAIL_SUBJECT);
			    }

			    email.getBody(new BodyChecker());
			    email.getAttachments(new AttachmentsChecker());
			    
			} else {
				System.out.println("Not an email");
			}
		}
	}

	// methods
	public void runSendReceiveTest() {

		// Create the sender
		EmailService sender;
		EmailService receiver;

		EmailService[] services;

		services = this.createEmailServices(new String[]
		    {"user1", "user2", "3", "4", "5", "6"}, 2000);
		
		try {
		    Thread.sleep(2000);
		} catch(InterruptedException ie) {
		    System.out.println("INTERRUPTED!: " + ie);
		    return;
		}

		sender = services[0];
		receiver = services[1];

		sender.addObserver(new EmailReceiver());
		receiver.addObserver(new EmailReceiver());

		// create an email to send
		String subject = EMAIL_SUBJECT;
		String bodyText = EMAIL_BODY;
		
		EmailData body = new EmailData(bodyText.getBytes());
		EmailData[] attachments = new EmailData[0];

		PostEntityAddress[] recipients = new PostEntityAddress[1];
		recipients[0] = receiver.getPost().getEntityAddress();

		Email email = new Email((PostUserAddress) sender.getPost().getEntityAddress(),
					recipients, subject,
					body, attachments);

		try {
			sender.sendMessage(email, new SENullCont());
		} catch(Exception e) {
			System.err.println("Error sending email");
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws InterruptedException {

		SendEmailTest set = new SendEmailTest();

		set.runSendReceiveTest();

		Thread.sleep(5000);
	}

  // inner class, added by JM so that sendMessage will have a continuation that is not null.
  private class SENullCont implements Continuation {
    SENullCont() {}
    public void receiveResult(Object result) {}
    public void receiveException(Exception result) {}
  }
}



