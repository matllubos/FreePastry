package rice.email.test;

import rice.email.*;
import rice.email.messaging.*;
import rice.post.*;
import java.util.*;

public class SendEmailTest extends EmailTest {

	// inner classes
	private class EmailReceiver implements Observer {
		public void update(Observable src, Object args) {
			System.out.println("SRC: " + src + ", ARG: " + args);

			if(args instanceof EmailNotificationMessage) {
				System.out.println("Email received");
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
		    {"user1", "user2"}, 2000);
		
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
	}

	public static void main(String[] args) throws InterruptedException {

		SendEmailTest set = new SendEmailTest();

		set.runSendReceiveTest();

		Thread.sleep(5000);
	}
}
