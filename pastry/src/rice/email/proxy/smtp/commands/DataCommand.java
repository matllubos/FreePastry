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

      logger.logException(Logger.SEVERE,
          "SEVERE: Exception " + je + " occurred while attempting to send message to " + msg.getRecipientIterator().next(), 
          je);
    }
    
    state.clearMessage();
  }
}