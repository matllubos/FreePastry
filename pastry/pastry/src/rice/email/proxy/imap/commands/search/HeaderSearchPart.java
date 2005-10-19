package rice.email.proxy.imap.commands.search;

import java.util.*;

import rice.*;
import rice.Continuation.*;

import rice.email.*;
import rice.email.proxy.imap.ImapConnection;
import rice.email.proxy.mail.StoredMessage;
import rice.email.proxy.mail.*;
import rice.email.proxy.mailbox.*;

import javax.mail.*;
import javax.mail.internet.*;

import java.io.*;

public class HeaderSearchPart extends SearchPart {

  String header;
  String string;
  
  public HeaderSearchPart(String header, String string) {
    this.header = header;
    this.string = string;
  }

  public boolean includes(StoredMessage msg) {
    try {
      ExternalContinuation c = new ExternalContinuation();
      msg.getMessage().getContent(c);
      c.sleep();

      if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

      EmailMessagePart message = (EmailMessagePart) c.getResult();
      
      c = new ExternalContinuation();
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