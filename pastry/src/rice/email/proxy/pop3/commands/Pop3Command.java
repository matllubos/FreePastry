package rice.email.proxy.pop3.commands;

import rice.email.proxy.pop3.*;

public abstract class Pop3Command {
  
    public abstract boolean isValidForState(Pop3State state);
    public abstract void execute(Pop3Connection conn, Pop3State state, String cmd);
    
}