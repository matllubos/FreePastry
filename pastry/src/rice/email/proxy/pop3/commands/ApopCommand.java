package rice.email.proxy.pop3.commands;

import rice.email.proxy.mailbox.*;
import rice.email.proxy.pop3.*;
import rice.email.proxy.user.*;

import rice.post.security.*;

public class ApopCommand extends Pop3Command {
  
  public boolean isValidForState(Pop3State state) {
    return !state.isAuthenticated();
  }
  
  public void execute(Pop3Connection conn, Pop3State state, String cmd) {
    try {
      String[] arguments = cmd.split(" ");
      String username = arguments[1];
      String authentication = arguments[2].toLowerCase();

      String password = state.getPassword(username);
      String digest = SecurityUtils.toHex(SecurityUtils.apop(state.getChallenge().getBytes(), password.getBytes())).toLowerCase();
            
      if (! digest.equals(authentication))
        throw new UserException("Incorrect password");

      state.setUser(state.getUser(username));
      conn.println("+OK");
    } catch (MailboxException me) {
      conn.println("-ERR " + me);
    } catch (UserException nsue) {
      conn.println("-ERR " + nsue);
    }
    
  }
}