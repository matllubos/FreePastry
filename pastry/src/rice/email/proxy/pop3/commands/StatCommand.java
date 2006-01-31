package rice.email.proxy.pop3.commands;

import rice.*;
import rice.Continuation.*;

import rice.email.*;
import rice.email.proxy.mail.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.pop3.*;

import java.util.Iterator;
import java.util.List;

public class StatCommand extends Pop3Command {
  
  public boolean isValidForState(Pop3State state) {
    return state.isAuthenticated();
  }
  
  public void execute(Pop3Connection conn, Pop3State state, String cmd) {
    try {
      MailFolder inbox = state.getFolder();
      List messages = inbox.getMessages(MsgFilter.NOT(MsgFilter.DELETED));
      long size = sumMessageSizes(messages);
      conn.println("+OK " + messages.size() + " " + size);
    } catch (Exception me) {
      conn.println("-ERR " + me);
    }
  }
  
  long sumMessageSizes(List messages) throws MailboxException, MailException {
    long total = 0;
    
    for (Iterator i = messages.iterator(); i.hasNext();) {
      StoredMessage msg = (StoredMessage) i.next();
      
      total += (long) msg.getMessage().getContent().getSize();
    }
    
    return total;
  }
}