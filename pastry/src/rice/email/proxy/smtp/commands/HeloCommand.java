package rice.email.proxy.smtp.commands;

import rice.email.proxy.smtp.SmtpConnection;
import rice.email.proxy.smtp.SmtpState;
import rice.email.proxy.smtp.manager.SmtpManager;


/**
 * EHLO/HELO command.
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
public class HeloCommand
    extends SmtpCommand
{
  
  public boolean authenticationRequired() { return false; }
  
    public void execute(SmtpConnection conn, SmtpState state, 
                        SmtpManager manager, String commandLine)
    {
        extractHeloName(conn, commandLine);
        state.clearMessage();
        conn.println("250 " + conn.getServerGreetingsName() + " Hello " + conn.getHeloName() + ", pleased to meet you");
    }

    protected void extractHeloName(SmtpConnection conn, 
                                 String commandLine)
    {
        String heloName;

        if (commandLine.length() > 5)
            heloName = commandLine.substring(5);
        else
            heloName = null;

        conn.setHeloName(heloName);
    }
}