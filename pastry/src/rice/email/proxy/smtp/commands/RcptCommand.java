package rice.email.proxy.smtp.commands;

import rice.email.proxy.mail.MailAddress;
import rice.email.proxy.mail.MalformedAddressException;

import rice.email.proxy.smtp.SmtpConnection;
import rice.email.proxy.smtp.SmtpState;
import rice.email.proxy.smtp.manager.SmtpManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * RCPT command.
 * 
 * <p>
 * The spec is at <a
 * href="http://asg.web.cmu.edu/rfc/rfc2821.html#sec-4.1.1.3">
 * http://asg.web.cmu.edu/rfc/rfc2821.html#sec-4.1.1.3</a>.
 * </p>
 */
public class RcptCommand extends SmtpCommand {
  static Pattern param = Pattern.compile("RCPT TO:\\s?<([^>]+)>", Pattern.CASE_INSENSITIVE);
  
  public void execute(SmtpConnection conn, SmtpState state, SmtpManager manager, String commandLine) {
    Matcher m = param.matcher(commandLine);
    
    try {
      if (m.matches()) {
        if (state.getMessage().getReturnPath() != null) {
          String to = m.group(1);
          
          MailAddress toAddr = new MailAddress(to);
          
          String err = manager.checkRecipient(state, toAddr);
          
          if (err != null) {
            conn.println("554 Error: " + err);
            conn.quit();
            return;
          }
          
          state.getMessage().addRecipient(toAddr);
          conn.println("250 " + to + "... Recipient OK");
        } else {
          conn.println("503 MAIL must come before RCPT");
        }
      } else {
        conn.println("501 Required syntax: 'RCPT TO:<email@host>'");
      }
    } catch (MalformedAddressException e) {
      conn.println("501 Malformed email address. Use form email@host");
    }
  }
}