package rice.email.proxy.imap.commands.search;

import java.io.ByteArrayInputStream;
import java.util.Enumeration;

import javax.mail.MessagingException;
import javax.mail.internet.InternetHeaders;

import rice.Continuation.ExternalContinuation;
import rice.email.EmailData;
import rice.email.EmailMessagePart;
import rice.email.proxy.mail.StoredMessage;
import rice.email.proxy.mailbox.MailboxException;

public class HeaderSearchPart extends SearchPart {

  String header;
  String string;
  
  public HeaderSearchPart(String header, String string) {
    this.header = header;
    this.string = string;
  }

  public boolean includes(StoredMessage msg) {
    try {
      final EmailMessagePart message = msg.getMessage().getContent();
      
      ExternalContinuation c = new ExternalContinuation();
      message.getHeaders(c);
      c.sleep();

      if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

      InternetHeaders headers = new InternetHeaders(new ByteArrayInputStream(((EmailData) c.getResult()).getData()));
      
      Enumeration e = headers.getMatchingHeaderLines(new String[] {header});

      while (e.hasMoreElements()) {
        if (((String) e.nextElement()).toLowerCase().indexOf(string.toLowerCase()) >= 0) {
          return true;
        }
      }

      return false;
    } catch (MailboxException e) {
      System.out.println("Exception " + e + " was thrown in HeaderSearchPart.");
      return false;
    } catch (MessagingException e) {
      System.out.println("Exception " + e + " was thrown in HeaderSearchPart.");
      return false;
    }
  }
}