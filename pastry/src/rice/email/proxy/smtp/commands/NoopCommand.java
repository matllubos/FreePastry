package rice.email.proxy.smtp.commands;

import rice.email.proxy.smtp.SmtpConnection;
import rice.email.proxy.smtp.SmtpState;
import rice.email.proxy.smtp.manager.SmtpManager;


/**
 * NOOP command.
 * 
 * <p>
 * The spec is at <a
 * href="http://asg.web.cmu.edu/rfc/rfc2821.html#sec-4.1.1.9">
 * http://asg.web.cmu.edu/rfc/rfc2821.html#sec-4.1.1.9</a>.
 * </p>
 */
public class NoopCommand
    extends SmtpCommand
{
    public void execute(SmtpConnection conn, SmtpState state, 
                        SmtpManager manager, String commandLine)
    {
        conn.println("250 Is that all?");
    }
  
  public boolean authenticationRequired() { return false; }
}