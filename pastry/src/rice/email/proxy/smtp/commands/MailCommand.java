package rice.email.proxy.smtp.commands;

import rice.email.proxy.mail.MailAddress;
import rice.email.proxy.mail.MalformedAddressException;

import rice.email.proxy.smtp.SmtpConnection;
import rice.email.proxy.smtp.SmtpState;
import rice.email.proxy.smtp.manager.SmtpManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * MAIL command.
 * 
 * <p>
 * The spec is at <a
 * href="http://asg.web.cmu.edu/rfc/rfc2821.html#sec-4.1.1.2">
 * http://asg.web.cmu.edu/rfc/rfc2821.html#sec-4.1.1.2</a>.
 * </p>
 */
public class MailCommand extends SmtpCommand {
  static final Pattern param = Pattern.compile("MAIL FROM:\\s?<(.*)>", Pattern.CASE_INSENSITIVE);
  
  public boolean authenticationRequired() { return true; }
  
  public void execute(SmtpConnection conn, SmtpState state, SmtpManager manager, String commandLine) {
    Matcher m = param.matcher(commandLine);
    try {
      if (m.matches()) {
        String from = m.group(1);
        MailAddress fromAddr = new MailAddress(from);
        
        String err = manager.checkSender(conn, state, fromAddr);
        if (err != null) {
          conn.println("554 Error: " + err);
          conn.quit();
          return;
        }
        
        state.clearMessage();
        state.getMessage().setReturnPath(fromAddr);
        conn.println("250 " + from + "... Sender OK");
      } else {
        conn.println("501 Required syntax: 'MAIL FROM:<email@host>'");
      }
    } catch (MalformedAddressException e) {
      conn.println("501 Malformed email address. Use form email@host");
    }
  }
}