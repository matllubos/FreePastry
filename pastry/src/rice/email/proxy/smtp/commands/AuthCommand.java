package rice.email.proxy.smtp.commands;

import java.io.*;
import java.net.*;
import rice.p2p.util.*;

import rice.email.proxy.smtp.SmtpConnection;
import rice.email.proxy.smtp.SmtpState;
import rice.email.proxy.smtp.manager.SmtpManager;
import rice.email.proxy.user.*;

/**
 * AUTH command.
 * 
 * <p>
 * TODO: What does HELO do if it's already been called before?
 * </p>
 * 
 * <p>
 * The spec is at <a
 * href="http://asg.web.cmu.edu/rfc/rfc2821.html#sec-4.1.1.1">
 * http://asg.web.cmu.edu/rfc/rfc2821.html#sec-4.1.1.1 </a>.
 * </p>
 */
public class AuthCommand extends SmtpCommand {
  
  public boolean authenticationRequired() { return false; }
  
  public void execute(SmtpConnection conn, SmtpState state, 
                      SmtpManager manager, String commandLine) {
    String mechanism = extractAuthMechanism(commandLine);
    
    if (mechanism == null) {
      conn.println("501 Required syntax: 'AUTH <mechanism>'"); 
    } else if (mechanism.toUpperCase().equals("CRAM-MD5")) {
      try {
        long timestamp = state.getEnvironment().getTimeSource().currentTimeMillis();
        String text = "<" + timestamp + "@" + InetAddress.getLocalHost().getHostName() + ">";
        conn.println("334 " + Base64.encodeBytes(text.getBytes()));
        String response = new String(Base64.decode(conn.readLine()));
        
        if (response.indexOf(" ") <= 0) {
          conn.println("535 Bad response - no username");
          return;
        }
        
        String username = response.substring(0, response.indexOf(" "));
        String password = state.getPassword(username);        
        String authentication = response.substring(response.indexOf(" ") + 1).toLowerCase();
        String digest = MathUtils.toHex(SecurityUtils.hmac(password.getBytes(), text.getBytes())).toLowerCase();
        
        if (! digest.equals(authentication)) {
          conn.println("535 Incorrect password");
          return;
        }
        
        User user = state.getUser(username);
        state.setUser(user);
        
        conn.println("235 Authentication successful");
      } catch (UnknownHostException e) {
        conn.println("535 Unknown local host - configuration error.");
      } catch (IOException e) {
        conn.println("535 I/O Error - please try again.");
      } catch (UserException e) {
        conn.println("535 User error - please try again.");
      } catch (NullPointerException e) {
        conn.println("535 Internal error " + e + " - please try again.");
        e.printStackTrace();
      }
    } else if (mechanism.toUpperCase().equals("LOGIN")) {
      try {
        conn.println("334 " + Base64.encodeBytes("Username".getBytes()));
        String username = new String(Base64.decode(conn.readLine()));
        conn.println("334 " + Base64.encodeBytes("Password".getBytes()));
        String authentication = new String(Base64.decode(conn.readLine()));
        String password = state.getPassword(username);        
        
        if (! password.equals(authentication)) {
          conn.println("535 Incorrect password");
          return;
        }
        
        User user = state.getUser(username);
        state.setUser(user);
        
        conn.println("235 Authentication successful");
      } catch (IOException e) {
        conn.println("535 I/O Error - please try again.");
      } catch (UserException e) {
        conn.println("535 User error - please try again.");
      } catch (NullPointerException e) {
        conn.println("535 Internal error " + e + " - please try again.");
        e.printStackTrace();
      }
    } else {
      conn.println("504 Unrecognized authentication type '" + mechanism + "'"); 
    }
  }

  private String extractAuthMechanism(String commandLine) {
    if (commandLine.length() > 5)
      return commandLine.substring(5);
    else
      return null;
  }
}
