package rice.email.test;

import rice.email.*;
import rice.post.*;
import java.util.*;

public class SendEmailTest extends EmailTest {

	// inner classes
	private class EmailReceiver implements Observer {
		public void update(Observable src, Object args) {
			System.out.println("SRC: " + src + ", ARG: " + args);

			if(args instanceof Email) {
				System.out.println("Email received");
			} else {
				System.out.println("Not an email");
			}
		}
	}

	// methods
	public void runSendReceiveTest() {

		// Create the sender
		EmailService sender = this.createEmailService("user1");

		// create the receiver
		EmailService receiver = this.createEmailService("user2");
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
