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
      
      ExternalContinuation c = new ExternalContinuation();
      msg.getMessage().getContent(c);
      c.sleep();
      
      if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }
      
      EmailMessagePart message = (EmailMessagePart) c.getResult();
      
      conn.println("+OK");
      conn.println(fetchAll(message));
      conn.println(".");
    } catch (Exception e) {
      conn.println("-ERR " + e);
    }
  }
  
  public static String fetchAll(EmailContentPart part) throws MailboxException {
    if (part instanceof EmailMultiPart)
      return fetchAll((EmailMultiPart) part);
    else if (part instanceof EmailSinglePart)
      return fetchAll((EmailSinglePart) part);
    else if (part instanceof EmailHeadersPart)
      return fetchAll((EmailHeadersPart) part);
    else
      throw new MailboxException("Unrecognized part " + part);
  }
  
  public static String fetchAll(EmailMultiPart part) throws MailboxException {
    String type = part.getType();
    String seperator = type.substring(type.toLowerCase().indexOf("boundary=")+9, type.length());
    
    if (seperator.indexOf(";") >= 0)
      seperator = seperator.substring(0, seperator.indexOf(";"));
    
    seperator = seperator.replaceAll("\"", "").replaceAll("'", "");
    StringBuffer result = new StringBuffer();
    
    ExternalContinuation c = new ExternalContinuation();
    part.getContent(c);
    c.sleep();
    
    if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }
    
    EmailContentPart[] parts = (EmailContentPart[]) c.getResult();
    
    for (int i=0; i<parts.length; i++) {
      result.append("--" + seperator + "\r\n" + fetchAll(parts[i]) + "\r\n");
    }
    
    result.append("--" + seperator + "--\r\n");
    
    return result.toString();
  }
  
  public static String fetchAll(EmailSinglePart part) throws MailboxException {
    ExternalContinuation c = new ExternalContinuation();
    part.getContent(c);
    c.sleep();
    
    if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }
    
    return new String(((EmailData) c.getResult()).getData());
  }
  
  public static String fetchAll(EmailHeadersPart part) throws MailboxException {
    ExternalContinuation c = new ExternalContinuation();
    part.getHeaders(c);
    c.sleep();
    
    if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }
    
    EmailData headers = (EmailData) c.getResult();
    
    c = new ExternalContinuation();
    part.getContent(c);
    c.sleep();
    
    if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }
    
    EmailContentPart data = (EmailContentPart) c.getResult();
    
    return new String(headers.getData()) + "\r\n" + fetchAll(data);
  }
  
}