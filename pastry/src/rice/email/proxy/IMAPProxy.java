package rice.email.proxy;

import java.util.Properties;
import java.util.Observer;
import java.util.Observable;

import java.net.BindException;
import java.net.InetAddress;
import javax.mail.*;
import javax.mail.internet.*;
import com.sun.mail.imap.*;

import rice.email.*;
import rice.email.messaging.*;
import rice.post.*;


/**
 * This class provides an IMAP interface into the POST pastry-based email system.  This will
 * allow IMAP-compliant email clients to interact with the POST library.  This proxy, with proper
 * configuration, can service multiple POST accounts (and therefore multiple, simultaneous IMAP
 * clients).
 * 
 * @author Derek Ruths
 */
public class IMAPProxy implements Observer {

    private final int STATE_INITIAL = 1;
    private final int STATE_GETTING_BODY = 2;
    private final int STATE_FINISHED = 3;

    InetAddress address;
    int port;

    EmailService service;

    IMAPStore imapStore;

    Session session;
  
  // constructors
  /**
   * This constructor instantiates the proxy.  The address and port specified instruct the
   * IMAP service of the port and address it should bind to locally.
   * 
   * @param address is the address of our remote IMAP server
   * @param port is the IMAP port to connect to on the server
   * 
   */
  public IMAPProxy(InetAddress address, int port) {
      this.address = address;
      this.port = port;
  }
  
  // methods
  /**
   * This method attaches this IMAP proxy to a specific EmailService
   * 
   * @param service is the EmailService to connect into
   * @param imapUsername is the username that an IMAP-based email reader will use to
   * access this account through this proxy.
   * @param imapPassword is the password that an IMAP-based email reader will use in 
   * combination with the username to access this account through this proxy.
   */
  public void attach(EmailService service, String imapUsername, String imapPassword) {

      this.session = Session.getInstance(System.getProperties());

      // Note the service
      this.service = service;

      // Log into an IMAP server
      URLName imapURL = new URLName("imap", address.getHostName(), port, "", imapUsername, imapPassword);
      imapStore = new IMAPStore(session, imapURL);
      try {
	  imapStore.connect();
      } catch (MessagingException e) {
	  // FIXME - do something here
	  System.err.println("Error connecting to IMAP server");
	  System.out.println(e);
	  e.printStackTrace();
      }

      // Finally add ourselves as a listener so that we hear about incoming mail.
      service.addObserver(this);
      System.out.println("IMAPProxy started successfully.");
  }
    
  /**
   * This method is called on this object when an email is received by an
   * EmailService object.
   * 
   * At the moment, by default, emails are put in the INBOX folder regardless. This may need
   * to change later.
   * 
   * @param email is the email that was received.
   */
  public void update(Observable updater, Object emailNote) {

      Email email = ((EmailNotificationMessage) emailNote).getEmail();

      // Save that email in a continuation and have it deal with it.
      IMAPContinuation ic = new IMAPContinuation(email);

      MimeMessage message = ic.getMessage();
      message.setSender(new
	  InternetAddress(toInetEmail(email.getSender())));
      message.setSubject(email.getSubject());

      // TODO: Handle groups

      // Recipients
      PostEntityAddress[] recips = email.getRecipients();
      Address[] container = new Address[1];
      for (int i = 0; i < recips.length; i++) {
	  container[0] = new InternetAddress(toInetEmail((PostUserAddress) recips[i]));
	  message.addRecipients(Message.RecipientType.TO, container);
      }

      // Set the message's contents to an empty body, arrange to have
      // it filled in
      Multipart emailContents = new MimeMultipart();
      message.setContent(emailContents);

      // Get the body and remember the continuation
      ic.setState(STATE_GETTING_BODY);
      email.getBody(ic);	
  }
    
    /**
     * This method is called by the continuation when the email body
     * has been fetched.
     *
     * @param ic The IMAPContinuation in question
     * @param body The body of the email that was fetched
     */
    protected void update_gotbody(IMAPContinuation ic, EmailData body) {

	MimeBodyPart mimeBody = new MimeBodyPart();
	mimeBody.setText(new String(body.getData()));
	
	// Add that newly constructed body part in.
	((Multipart)
	 ic.getMessage().getContent()).addBodyPart(mimeBody);

	// Finish up (FIXME do attachments)
	deliverMessage(ic.getMessage());	
    }

    /**
     * Called when the MIME message has been completely built up
     * @param message The message to put in the inbox.
     */
    private void deliverMessage(MimeMessage message) {
	try {
	    javax.mail.Folder thisFolder =
		imapStore.getFolder("INBOX");
	    Message[] messageArray = new Message[1];
	    messageArray[0] = message;
	    thisFolder.appendMessages(messageArray);
	} catch (MessagingException e) {
	    System.err.println("Error delivering message: " + e);
	}
    }
    
    private String toInetEmail(PostUserAddress address) {
	return address.getName();
    }
}


