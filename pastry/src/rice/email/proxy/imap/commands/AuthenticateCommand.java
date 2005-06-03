package rice.email.proxy.imap.commands;

import java.io.*;
import java.net.*;

import rice.p2p.util.*;
import rice.email.proxy.imap.ImapState;
import rice.email.proxy.user.User;
import rice.email.proxy.user.UserException;
import rice.environment.Environment;


/**
 * AUTHENTICATE command.
 * 
 * <p>
 * <a href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.2.2">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.2.2 </a>
 * </p>
 */
public class AuthenticateCommand extends AbstractImapCommand {
  
  String type;
  String username;
  
  public AuthenticateCommand() {
    super("AUTHENTICATE");
  }
  
  public boolean isValidForState(ImapState state) {
    return !state.isAuthenticated();
  }
  
  public void execute() {
    try {
      if (! type.equals("CRAM-MD5"))
        throw new IllegalArgumentException("Authentication Mechanism " + type + " not supported.");
      
      long timestamp = _state.getEnvironment().getTimeSource().currentTimeMillis();
      String text = "<" + timestamp + "@" + InetAddress.getLocalHost().getHostName() + ">";
      getConn().println("+ " + Base64.encodeBytes(text.getBytes()));
      String response = new String(Base64.decode(getConn().readLine()));
      
      if (response.indexOf(" ") <= 0)
        throw new IllegalArgumentException("Bad response - no username");
      
      username = response.substring(0, response.indexOf(" "));
      String password = getState().getPassword(username);
      String authentication = response.substring(response.indexOf(" ") + 1).toLowerCase();
      String digest = MathUtils.toHex(SecurityUtils.hmac(password.getBytes(), text.getBytes())).toLowerCase();
      
      if (! digest.equals(authentication))
        throw new UserException("Incorrect password");

      User user = getState().getUser(username);
      getState().setUser(user);
      taggedSimpleSuccess(); 
    } catch (IllegalArgumentException nsue) {
      taggedExceptionFailure(nsue);
    } catch (UnknownHostException nsue) {
      taggedExceptionFailure(nsue);
    } catch (IOException nsue) {
      taggedExceptionFailure(nsue);
    } catch (UserException nsue) {
      taggedExceptionFailure(nsue);
    }
  }
  
  public String getType() {
    return type;
  }
  
  public void setType(String type) {
    this.type = type;
  }
}
