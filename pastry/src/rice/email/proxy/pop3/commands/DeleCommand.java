package rice.email.proxy.pop3.commands;

import rice.email.proxy.mail.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.pop3.*;
import java.util.List;


public class DeleCommand extends Pop3Command {
  
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
      FlagList flags = msg.getFlagList();
      
      if (flags.isDeleted())  {
        conn.println("-ERR message already deleted");
        return;
      }
      
      msg.getFlagList().setDeleted(true);
      flags.commit();
      
      conn.println("+OK message deleted");
    } catch (Exception e) {
      conn.println("-ERR " + e);
    }
  }
}