/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/
package rice.email.proxy.smtp.commands;

import java.io.*;
import java.net.*;
import rice.p2p.util.*;

import rice.email.proxy.smtp.SmtpConnection;
import rice.email.proxy.smtp.SmtpState;
import rice.email.proxy.smtp.manager.SmtpManager;
import rice.email.proxy.user.*;
import rice.environment.logging.Logger;

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
        String text = "<" + timestamp + "@" + manager.getLocalHost().getHostName() + ">";
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
        Logger logger = state.getEnvironment().getLogManager().getLogger(AuthCommand.class, null);
        if (logger.level <= Logger.WARNING) logger.logException(
            "535 Internal error " + e + " - please try again.", e);
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
        Logger logger = state.getEnvironment().getLogManager().getLogger(AuthCommand.class, null);
        if (logger.level <= Logger.WARNING) logger.logException(
            "535 Internal error " + e + " - please try again.", e);
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
