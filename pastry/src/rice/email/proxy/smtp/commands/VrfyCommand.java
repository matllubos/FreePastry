package rice.email.proxy.smtp.commands;

import rice.email.proxy.smtp.SmtpConnection;
import rice.email.proxy.smtp.SmtpState;
import rice.email.proxy.smtp.manager.SmtpManager;


/**
 * VRFY command.
 * 
 * <p>
 * The spec is at <a
 * href="http://asg.web.cmu.edu/rfc/rfc2821.html#sec-4.1.1.6">
 * http://asg.web.cmu.edu/rfc/rfc2821.html#sec-4.1.1.6</a>.
 * </p>
 */
public class VrfyCommand extends SmtpCommand {
  public void execute(SmtpConnection conn, SmtpState state, SmtpManager manager, String commandLine) {
    conn.println("252 Cannot VRFY user, but will accept message and attempt delivery");
  }
}