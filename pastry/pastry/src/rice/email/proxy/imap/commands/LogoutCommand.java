package rice.email.proxy.imap.commands;

import rice.email.proxy.imap.ImapState;


/**
 * LOGOUT command.
 * 
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.1.3">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.1.3 </a>
 * </p>
 */
public class LogoutCommand
    extends AbstractImapCommand
{
    public LogoutCommand()
    {
        super("LOGOUT");
    }

    public boolean isValidForState(ImapState state)
    {

        return true;
    }

    public void execute()
    {
        getState().logout();
        getConn().quit();
        untaggedResponse("BYE IMAP4rev1 Server logging out");
        taggedSimpleSuccess();
    }
}