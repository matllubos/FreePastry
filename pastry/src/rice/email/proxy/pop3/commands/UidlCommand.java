package rice.email.proxy.pop3.commands;

import rice.email.proxy.mail.*;

import rice.email.proxy.mailbox.*;
import rice.email.proxy.pop3.*;

import java.util.Iterator;
import java.util.List;

public class UidlCommand extends Pop3Command {
  
  public boolean isValidForState(Pop3State state) {
    return state.isAuthenticated();
  }
  
  public void execute(Pop3Connection conn, Pop3State state, String cmd) {
    try {
      MailFolder inbox = state.getFolder();
      String[] cmdLine = cmd.split(" ");
      List messages;
      if (cmdLine.length > 1)
      {
        String msgNumStr = cmdLine[1];
        List msgList = inbox.getMessages(new MsgRangeFilter(msgNumStr, false));
        if (msgList.size() != 1) {
          conn.println("-ERR no such message");
          return;
        }
        
        StoredMessage msg = (StoredMessage) msgList.get(0);
        conn.println("+OK " + msgNumStr + " " + msg.getUID());
      } else {
        messages = inbox.getMessages(MsgFilter.NOT(MsgFilter.DELETED));
        
        conn.println("+OK");
        for (Iterator i = messages.iterator(); i.hasNext();) {
          StoredMessage msg = (StoredMessage) i.next();
          conn.println(msg.getSequenceNumber() + " " + msg.getUID());
        }
        
        conn.println(".");
      }
    } catch (MailboxException me) {
      conn.println("-ERR " + me);
    }
  }
}