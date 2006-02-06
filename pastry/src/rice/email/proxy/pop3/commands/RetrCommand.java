package rice.email.proxy.pop3.commands;

import rice.*;
import rice.Continuation.*;

import rice.email.*;
import rice.email.proxy.mail.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.pop3.*;

import java.io.Reader;
import java.util.List;

public class RetrCommand extends Pop3Command {
  
  public boolean isValidForState(Pop3State state) {
    return state.isAuthenticated();
  }
  
  public void execute(Pop3Connection conn, Pop3State state, String cmd) {
    try {
      MailFolder inbox = state.getFolder();
      String[] cmdLine = cmd.split(" ");
      
      String msgNumStr = cmdLine[1];
      List msgList = inbox.getMessages(new MsgRangeFilter(msgNumStr, false));
      if (msgList.size() != 1) {
        conn.println("-ERR no such message");
        return;
      }
      
      StoredMessage msg = (StoredMessage) msgList.get(0);
      
      EmailMessagePart message = msg.getMessage().getContent();
      
      conn.println("+OK");
      conn.println(fetchAll(conn, message));
      conn.println(".");
    } catch (Exception e) {
      conn.println("-ERR " + e);
    }
  }
  
  public static String fetchAll(Pop3Connection conn, EmailContentPart part) throws MailboxException {
    if (part instanceof EmailMultiPart)
      return fetchAll(conn, (EmailMultiPart) part);
    else if (part instanceof EmailSinglePart)
      return fetchAll(conn, (EmailSinglePart) part);
    else if (part instanceof EmailHeadersPart)
      return fetchAll(conn, (EmailHeadersPart) part);
    else
      throw new MailboxException("Unrecognized part " + part);
  }
  
  public static String fetchAll(Pop3Connection conn, final EmailMultiPart part) throws MailboxException {
    String type = part.getType();
    String seperator = type.substring(type.toLowerCase().indexOf("boundary=")+9, type.length());
    
    if (seperator.indexOf(";") >= 0)
      seperator = seperator.substring(0, seperator.indexOf(";"));
    
    seperator = seperator.replaceAll("\"", "").replaceAll("'", "");
    StringBuffer result = new StringBuffer();

    EmailContentPart[] parts;
    try {
      ExternalContinuationRunnable c = new ExternalContinuationRunnable() {
        protected void run(Continuation c) {
          part.getContent(c);
        }
      };
      
      parts = (EmailContentPart[]) c.invoke(conn.getEnvironment());
    } catch (Exception e) {
      throw new MailboxException(e);
    }
    
    for (int i=0; i<parts.length; i++) {
      result.append("--" + seperator + "\r\n" + fetchAll(conn, parts[i]) + "\r\n");
    }
    
    result.append("--" + seperator + "--\r\n");
    
    return result.toString();
  }
  
  public static String fetchAll(Pop3Connection conn, final EmailSinglePart part) throws MailboxException {
    try {
      ExternalContinuationRunnable c = new ExternalContinuationRunnable() {
        protected void run(Continuation c) {
          part.getContent(c);
        }
      };
      
      return new String(((EmailData) c.invoke(conn.getEnvironment())).getData());
    } catch (Exception e) {
      throw new MailboxException(e);
    }
  }
  
  public static String fetchAll(Pop3Connection conn, final EmailHeadersPart part) throws MailboxException {
    try {
      ExternalContinuationRunnable c = new ExternalContinuationRunnable() {
        protected void run(Continuation c) {
          part.getHeaders(c);
        }
      };
       
      EmailData headers = (EmailData) c.invoke(conn.getEnvironment());
      
      c = new ExternalContinuationRunnable() {
        protected void run(Continuation c) {
          part.getContent(c);
        }
      };
  
      EmailContentPart data = (EmailContentPart) c.invoke(conn.getEnvironment());
      
      return new String(headers.getData()) + "\r\n" + fetchAll(conn, data);
    } catch (Exception e) {
      throw new MailboxException(e);
    }
  }
  
}