package rice.email.proxy.imap.commands;

import rice.email.proxy.imap.ImapConnection;
import rice.email.proxy.imap.ImapState;

import rice.email.proxy.mailbox.MailboxException;

import java.io.IOException;


/**
 * CREATE command.
 * 
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.3.3">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.3.3 </a>
 * </p>
 */
public class CreateCommand
    extends AbstractImapCommand
{
    String _folder;

    public CreateCommand()
    {
        super("CREATE");
    }

    public boolean isValidForState(ImapState state)
    {

        return state.isAuthenticated();
    }

    public void execute()
    {
        try
        {
            getState().getMailbox().createFolder(getFolder());
            taggedSimpleSuccess();
        }
        catch (MailboxException e)
        {
            taggedExceptionFailure(e);
        }
    }

    public String getFolder()
    {

        return _folder;
    }

    public void setFolder(String mailbox)
    {
        _folder = mailbox;
    }
}
