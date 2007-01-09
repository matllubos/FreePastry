/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
package rice.email.proxy.smtp.commands;

import rice.email.proxy.mail.MovingMessage;

import rice.email.proxy.smtp.*;
import rice.email.proxy.util.*;

import rice.email.proxy.smtp.manager.SmtpManager;
import rice.environment.logging.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;


/**
 * DATA command.
 * 
 * <p>
 * The spec is at <a
 * href="http://asg.web.cmu.edu/rfc/rfc2821.html#sec-4.1.1.4">
 * http://asg.web.cmu.edu/rfc/rfc2821.html#sec-4.1.1.4 </a>.
 * </p>
 */
public class DataCommand extends SmtpCommand {
  
  public boolean authenticationRequired() { return true; }
  
  public void execute(SmtpConnection conn, SmtpState state, SmtpManager manager, String commandLine) throws IOException {
    MovingMessage msg = state.getMessage();
    
    if (msg.getReturnPath() == null) {
      conn.println("503 MAIL command required");
      return;
    }
    
    if (! msg.getRecipientIterator().hasNext()) {
      conn.println("503 RCPT command(s) required");
      return;
    }
    
    conn.println("354 Start mail input; end with <CRLF>.<CRLF>");
    
    String value = "Return-Path: <" + msg.getReturnPath() + 
      ">\r\n" + "Received: from " + 
      conn.getClientAddress() + " (HELO " + 
      conn.getHeloName() + "); " + 
      new java.util.Date() + "\r\n";
    
    try {
      msg.readDotTerminatedContent(conn, state.getEnvironment());
    } catch (StringWriterOverflowException e) {
      conn.println("554 Error: Requested action not taken: message too large");
      conn.quit();
      return;
    }
    
    String err = manager.checkData(state);
    if (err != null) {
      conn.println("552 Error: " + err);
      conn.quit();
      return;
    }
    
    try {
      manager.send(state, conn.isLocal());
      conn.println("250 Message accepted for delivery");
      conn.getServer().incrementSuccess();
    } catch (Exception je) {
      conn.println("451 Requested action aborted: local error in processing");
      conn.getServer().incrementFail();
      Logger logger = state.getEnvironment().getLogManager().getLogger(getClass(), null);

      if (logger.level <= Logger.SEVERE) logger.logException(
          "SEVERE: Exception " + je + " occurred while attempting to send message to " + msg.getRecipientIterator().next(), 
          je);
    }
    
    state.clearMessage();
  }
}