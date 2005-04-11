package rice.email.proxy.web.pages;

import rice.email.*;
import rice.*;
import rice.Continuation.*;

import javax.mail.*;
import javax.mail.internet.*;

import java.io.*;
import java.text.*;
import java.util.*;
import rice.email.proxy.mail.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.web.*;

public class MessagePage extends WebPage {
  
  public static final SimpleDateFormat DATE = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z");
  
  public boolean authenticationRequired() { return true; }
  
  public String getName() { return "/hierarchy"; }
  
	public void execute(WebConnection conn, final WebState state)	throws WebException, IOException {    
    try {
      writeHeader(conn);
      
      if (state.getCurrentMessageUID() < 0) {
        conn.print("<i>No message is selected</i>");
      } else { 
        System.out.println("MONKEY6!");

        List list = state.getCurrentFolder().getMessages(new MsgFilter() {
          public boolean includes(StoredMessage msg) {
            return msg.getUID() == state.getCurrentMessageUID();
          }
        });
        
        if (list.size() == 0) {
          conn.print("<i>That message no longer available.</i>");
        } else {
          StoredMessage message = (StoredMessage) list.get(0);
          
          Email email = message.getMessage();

          ExternalContinuation c = new ExternalContinuation();
          email.getContent(c);
          c.sleep();
          if (c.exceptionThrown()) throw new MailboxException(c.getException());

          EmailMessagePart real = (EmailMessagePart) c.getResult();
          ExternalContinuation d = new ExternalContinuation();
          real.getHeaders(d);
          d.sleep();
          if (d.exceptionThrown()) throw new MailboxException(d.getException());

          InternetHeaders headers = FolderPage.getHeaders((EmailData) d.getResult());
          
          ExternalContinuation e = new ExternalContinuation();
          real.getContent(e);
          e.sleep();
          if (e.exceptionThrown()) throw new MailboxException(e.getException());
          
          EmailContentPart part = (EmailContentPart) e.getResult();
          String body = null;
          
          if (part instanceof EmailSinglePart) {
            ExternalContinuation f = new ExternalContinuation();
            part.getContent(f);
            f.sleep();
            if (f.exceptionThrown()) throw new MailboxException(f.getException());

            EmailData data = (EmailData) f.getResult();
            body = new String(data.getData()).replaceAll("\r", "").replaceAll("\n", "<br>");
          }
          
          String from = FolderPage.getHeader(headers, "From");
          String to = FolderPage.getHeader(headers, "To");
          String subject = FolderPage.getHeader(headers, "Subject");
          Date date = new Date(message.getInternalDate());
          
          conn.print("<table>");
          conn.print("<tr><td><b>From:</b></td><td>" + from + "</td></tr>");
          conn.print("<tr><td><b>To:</b></td><td>" + to + "</td></tr>");
          conn.print("<tr><td><b>Subject:</b></td><td>" + subject + "</td></tr>");
          conn.print("<tr><td><b>Date:</b></td><td>" + DATE.format(date) + "</td></tr>");
          conn.print("</table><p>");
          conn.print(body);
        }
      } 
      
      writeFooter(conn);
    } catch (MailboxException e) {
      throw new WebException(conn.STATUS_ERROR, "An internal error has occured - '" + e + "'.");      
    }    
  }
}