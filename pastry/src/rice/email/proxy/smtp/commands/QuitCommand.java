package rice.email.proxy.smtp.commands;

import rice.email.proxy.smtp.SmtpConnection;
import rice.email.proxy.smtp.SmtpState;
import rice.email.proxy.smtp.manager.SmtpManager;


/**
 * QUIT command.
 * 
 * <p>
 * The spec is at <a
 * href="http://asg.web.cmu.edu/rfc/rfc2821.html#sec-4.1.1.10">
 * http://asg.web.cmu.edu/rfc/rfc2821.html#sec-4.1.1.10</a>.
 * </p>
 */
public class QuitCommand
    extends SmtpCommand
{
    public void execute(SmtpConnection conn, SmtpState state, 
                        SmtpManager manager, String commandLine)
    {
    	state.clearMessage();
        conn.println(
                "221 " + conn.getServerGreetingsName() + 
                " Service closing transmission channel");
        conn.quit();
    }
}