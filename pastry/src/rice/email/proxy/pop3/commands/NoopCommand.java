package rice.email.proxy.pop3.commands;

import rice.email.proxy.mailbox.*;
import rice.email.proxy.pop3.*;
import rice.email.proxy.user.*;

public class NoopCommand extends Pop3Command {
  
  public boolean isValidForState(Pop3State state) {
    return true;
  }
  
  public void execute(Pop3Connection conn, Pop3State state, String cmd) {
    conn.println("+OK");
  }
  
}