package rice.email.proxy;

import java.util.Properties;
import java.util.Observer;
import java.util.Observable;
import java.io.IOException;

import java.net.BindException;
import java.net.InetAddress;
import javax.mail.*;
import javax.mail.internet.*;
import javax.mail.event.*;
import com.sun.mail.imap.*;

import rice.email.*;
import rice.email.messaging.*;
import rice.post.*;
import rice.Continuation;


/**
 * This class provides an IMAP interface into the POST pastry-based email system.  This will
 * allow IMAP-compliant email clients to interact with the POST library.
 * 
 * @author Dave Price
 */
public class IMAPProxy implements Observer, MessageCountListener {

    private final int STATE_INITIAL = 1;
    private final int STATE_GETTING_BODY = 2;
    private final int STATE_GETTING_ATTACHMENTS = 3;
    private final int STATE_FINISHED = 4;

    InetAddress address;
    int port;

    EmailService service;

    IMAPStore imapStore;

    Session session;

    
    /**
     * The Continuation that represents the state of a single message
     * fetch. Stores the Email that's being read from and the
     * MimeMessage that's being built up from it; also stores the
     * current state of the process. This complies with the
     * continuation pattern used throughout PAST, Post, and
     * EmailService.
     */
    private class IMAPContinuation implements Continuation {
	IMAPProxy _parent;
	Email _email;
	MimeMessage _message;

	int state;

	public void setState (int newState) {
	    state = newState;
	}

	public IMAPContinuation(IMAPProxy parent, Email email) {
	    _parent = parent;
	    _email = email;
	    _message = new MimeMessage(parent.session);
	    state = STATE_INITIAL;
	}

	public MimeMessage getMessage() {
	    return _message;
	}

	public Email getEmail() {
	    return _email;
	}

	public void receiveResult(Object o) {

	    System.err.println("Received a continuation call");
	    switch(state) {
	    case STATE_INITIAL:
		System.err.println("Received a result in initial state!");
		break;
	    case STATE_GETTING_BODY:
		_parent.update_gotbody(this, (EmailData) o);
		break;
	    case STATE_GETTING_ATTACHMENTS:
		_parent.update_gotattachments(this, (EmailData[]) o);
		break;
	    case STATE_FINISHED:
		System.err.println("Received a result in final  state!");
		break;
	    }
	    
	}

	public void receiveException(Exception e) {
	    System.err.println("IMAP proxy received exception " + e);
	}
    }
  
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
   * @param imapUsername is the username to use when connecting to the
   * remote IMAP server
   * @param imapPassword is the password to use for same (be careful here!)
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

      System.err.println("IMAPProxy got an email");

      try {

	  Email email = ((EmailNotificationMessage) emailNote).getEmail();
	  
	  // Save that email in a continuation and have it deal with it.
	  IMAPContinuation ic = new IMAPContinuation(this, email);

	  System.err.println("Built a continuation");
	  
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

	  System.err.println("About to fetch the body of the message");
	  email.getBody(ic);
	  System.err.println("Called getBody.");
      } catch (MessagingException e) {
	  System.err.println("IMAP proxy error on update: " + e);
      }
  }
    
    /**
     * This method is called by the continuation when the email body
     * has been fetched.
     *
     * @param ic The IMAPContinuation in question
     * @param body The body of the email that was fetched
     */
    protected void update_gotbody(IMAPContinuation ic, EmailData body)
    {

	try {
	    
	    MimeBodyPart mimeBody = new MimeBodyPart();
	    mimeBody.setText(new String(body.getData()));
	    
	    // Add that newly constructed body part in.
	    ((Multipart)
	     ic.getMessage().getContent()).addBodyPart(mimeBody);
	    
	    // Get the attachments
	    ic.setState(STATE_GETTING_ATTACHMENTS);
	    ic.getEmail().getAttachments(ic);

	} catch (MessagingException e) {
	    System.err.println("IMAP proxy: messaging exception " +
			       e);
	} catch (IOException e) {
	    System.err.println("IMAP proxy: IO exception " + e);
	}
    }

    /**
     * This method is called by the Continuation when the attachments
     * to the message are available.
     */
    protected void update_gotattachments(IMAPContinuation ic,
					 EmailData[] attachments)
    {
	try {

	    for (int i = 0; i < attachments.length; i++) {

		MimeBodyPart mimeBody = new MimeBodyPart();
		mimeBody.setText(new String(attachments[i].getData()));
		((Multipart)
		 ic.getMessage().getContent()).addBodyPart(mimeBody);
	    }

	    ic.setState(STATE_FINISHED);
	    // Deliver the message
	    deliverMessage(ic.getMessage());	    
	} catch (MessagingException e) {
	    System.err.println("IMAP proxy: messaging exception " +
			       e);
	} catch (IOException e) {
	    System.err.println("IMAP proxy: IO exception " + e);
	}
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

    // Comply with the MessageCountListener pattern
    public void messagesAdded(MessageCountEvent e) {
    }

    public void messagesRemoved(MessageCountEvent e) {
    }
}
