package rice.email.proxy.pop3.commands;

import rice.email.proxy.mail.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.pop3.*;

import java.util.Iterator;
import java.util.List;

public class QuitCommand extends Pop3Command {
  
  public boolean isValidForState(Pop3State state) {
    return true;
  }
  
  public void execute(Pop3Connection conn, Pop3State state, String cmd) {
    try {
      MailFolder folder = state.getFolder();
      if (folder != null) {
        purgeDeletedMessaged(folder);
      }
      
      conn.println("+OK POP3 server signing off");
      conn.quit();
    } catch (MailboxException me) {
      conn.println("+OK Signing off, but message deletion failed");
      conn.quit();
    }
  }
  
  void purgeDeletedMessaged(MailFolder folder) throws MailboxException {
    List l = folder.getMessages(MsgFilter.DELETED);
    StoredMessage[] deleted = new StoredMessage[l.size()];
    
    for (int i=0; i<l.size(); i++) 
      deleted[i] = (StoredMessage) l.get(i);
    
    folder.purge(deleted);
  }
}