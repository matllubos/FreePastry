package rice.email.proxy.smtp.commands;

import rice.email.proxy.smtp.SmtpConnection;
import rice.email.proxy.smtp.SmtpState;
import rice.email.proxy.smtp.manager.SmtpManager;


/**
 * RSET command.
 * 
 * <p>
 * The spec is at <a
 * href="http://asg.web.cmu.edu/rfc/rfc2821.html#sec-4.1.1.5">
 * http://asg.web.cmu.edu/rfc/rfc2821.html#sec-4.1.1.5</a>.
 * </p>
 */
public class RsetCommand
    extends SmtpCommand
{
    public void execute(SmtpConnection conn, SmtpState state, 
                        SmtpManager manager, String commandLine)
    {
        state.clearMessage();
        conn.println("250 OK");
    }
  
  public boolean authenticationRequired() { return false; }
}