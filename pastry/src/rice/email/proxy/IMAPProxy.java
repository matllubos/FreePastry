package rice.email.proxy;

import java.util.Properties;

import java.net.BindException;
import java.net.InetAddress;
import javax.mail.*;
import javax.mail.internet.*;
import com.sun.mail.imap.*;

import rice.email.*;
import rice.post.*;


/**
 * This class provides an IMAP interface into the POST pastry-based email system.  This will
 * allow IMAP-compliant email clients to interact with the POST library.  This proxy, with proper
 * configuration, can service multiple POST accounts (and therefore multiple, simultaneous IMAP
 * clients).
 * 
 * @author Derek Ruths
 */
public class IMAPProxy {

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

      this.session = Session.getInstance(new Properties());

      // Note the service
      this.service = service;

      // Log into an IMAP server
      URLName imapURL = new URLName("imap", address.toString(), port, "", imapUsername, imapPassword);
      imapStore = new IMAPStore(session, imapURL);
      try {
	  imapStore.connect();
      } catch (MessagingException e) {
	  // FIXME - do something here
      }

      // Finally add ourselves as a listener so that we hear about incoming mail.
      //service.addEmailServiceListener(this);
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
  public void messageReceived(Email email) {
	
      // FIXME: have a way to use other folders?
      try {

	  // Get the inbox
	  javax.mail.Folder thisFolder = imapStore.getFolder("INBOX");
	  Message[] messageArray = new Message[1];

	  // Build a Message out of that Email, put it in the array container
	  messageArray[0] = unparseEmail(email);

	  // Append that Message to the specified folder.
	  thisFolder.appendMessages(messageArray);
      } catch (MessagingException e) {
	  // Do something sensible
      } 
  }

  private Message unparseEmail(Email email) throws MessagingException {
      MimeMessage message = new MimeMessage(session);

      // Build up this message

      // Sender (from field)
      message.setSender(new InternetAddress(toInetEmail(email.getSender())));

      // Subject field
      message.setSubject(email.getSubject());

      // TO DO: Handle groups
      
      // Recipients
      PostEntityAddress[] recips = email.getRecipients();
      Address[] container = new Address[1];
      for (int i = 0; i < recips.length; i++) {
	  container[0] = new InternetAddress(toInetEmail((PostUserAddress) recips[i]));
	  message.addRecipients(Message.RecipientType.TO, container);
      }

      Multipart emailContents = new MimeMultipart();

      // FIXME - Pull this email body out of its reference. For now we use
      // an empty body - a fairly uninteresting message is built...
      MimeBodyPart mimeBody = new MimeBodyPart();
      mimeBody.setText(new String(email.getBody().getData()));

      emailContents.addBodyPart(mimeBody);


      message.setContent(emailContents);

      return message;

  }    
    
  private String toInetEmail(PostUserAddress address) {
      return address.getName();
  }
}
