package rice.email.proxy.pop3.commands;

import rice.*;
import rice.Continuation.*;

import rice.email.*;
import rice.email.proxy.mail.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.pop3.*;

import java.util.Iterator;
import java.util.List;

public class ListCommand extends Pop3Command {
  
  public boolean isValidForState(Pop3State state) {
    return state.isAuthenticated();
  }
  
  public void execute(Pop3Connection conn, Pop3State state, String cmd) {
    try {
      MailFolder inbox = state.getFolder();
      String[] cmdLine = cmd.split(" ");
      List messages;
      if (cmdLine.length > 1) {
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
        
        conn.println("+OK " + msgNumStr + " " + message.getSize());
      } else {
        messages = inbox.getMessages(MsgFilter.NOT(MsgFilter.DELETED));
        
        conn.println("+OK");
        for (Iterator i = messages.iterator(); i.hasNext();) {
          StoredMessage msg = (StoredMessage) i.next();
          
          ExternalContinuation c = new ExternalContinuation();
          msg.getMessage().getContent(c);
          c.sleep();
          
          if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }
          
          EmailMessagePart message = (EmailMessagePart) c.getResult();
          
          conn.println(msg.getSequenceNumber() + " " + message.getSize());
        }
        
        conn.println(".");
      }
    } catch (Exception me) {
      conn.println("-ERR " + me);
    }
  }
}