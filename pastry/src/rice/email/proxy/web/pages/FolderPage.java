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

public class FolderPage extends WebPage {
  
  public static final int NUM_MESSAGES_PER_PAGE = 20;
  
  public static final SimpleDateFormat DATE = new SimpleDateFormat("MMM d");
  
  public boolean authenticationRequired() { return true; }
  
  public String getName() { return "/folder"; }
  
	public void execute(WebConnection conn, WebState state)	throws WebException, IOException {    
    String uid = conn.getParameter("message");
    
    if (uid != null) {
      state.setCurrentMessageUID(Integer.parseInt(uid));
      conn.redirect("main");
      return;
    }
    
    try {
      writeHeader(conn);
      if (state.getCurrentFolder() != null) {
        MailFolder folder = state.getCurrentFolder();
        final int exists = folder.getExists();
        conn.print("<b>" + folder.getFullName() + "</b><p>");
        conn.print("  <script>function setURL(indx) {top.location=indx;}</script>");
        conn.print("<table border=0 cellspacing=0 cellpadding=0><tr><td background=\"#AAAAAA\" >");
        conn.print("<table border=0 cellspacing=1 cellpadding=1 width=100%>");
        conn.print("  <tr><td width=20%><b><i>From:</i></b></td>");
        conn.print("<td background=\"#FFFFFF\" width=70%><b><i>Subject:</i></b></td>");
        conn.print("<td background=\"#FFFFFF\" width=10%><b><i>Date:</i></b></td></tr>");
        
        MsgFilter last = new MsgFilter() {
          public boolean includes(StoredMessage msg) {
            return msg.getSequenceNumber() > exists - NUM_MESSAGES_PER_PAGE; 
          }
        };
        
        Iterator messages = folder.getMessages(last).iterator();
        
        while (messages.hasNext()) {
          StoredMessage message = (StoredMessage) messages.next();
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
          
          InternetHeaders headers = getHeaders((EmailData) d.getResult());
          
          String from = getHeader(headers, "From").replaceAll("\"", "");
          String subject = getHeader(headers, "Subject");
          Date date = new Date(message.getInternalDate());
                    
          boolean unseen = (! message.getFlagList().isSeen());
          boolean selected = (message.getUID() == state.getCurrentMessageUID());

          String color="DDDDDFF";
          
          if (unseen)
            color="FFFFFF";
          
          conn.print("  <tr onClick=setURL('" + getName() + "?message=" + message.getUID() + "')>");
          conn.print("<td bgcolor=" + color + ">" + (unseen ? "<b>" : "") + from + (unseen ? "</b>": "") + "</td>");
          conn.print("<td bgcolor=" + color + ">" + (unseen ? "<b>": "") + subject + (unseen ? "</b>": "") + "</td>");
          conn.print("<td bgcolor=" + color + ">" + (unseen ? "<b>": "") + DATE.format(date) + (unseen ? "</b>": "") + "</td></tr>");
        }
        
        conn.print("</table>");
        conn.print("</td></tr></table>");
      } else {
        conn.print("<i>No folder is selected</i>");
      }
      writeFooter(conn);
    } catch (MailboxException e) {
      throw new WebException(conn.STATUS_ERROR, "An internal error has occured - '" + e + "'.");      
    }
  }
  
  protected static String getHeader(InternetHeaders headers, String header) throws MailboxException {
    String[] result = headers.getHeader(header);
    
    if ((result != null) && (result.length > 0)) {
      String text = result[0].replaceAll("\\n", "").replaceAll("\\r", "");
      return text;
    }
    
    return "<i>unknown</i>";
  }
  
  protected static InternetHeaders getHeaders(EmailData data) throws MailboxException {
    try {
      return new InternetHeaders(new ByteArrayInputStream(data.getData()));
    } catch (MessagingException e) {
      throw new MailboxException(e);
    }
  }
}