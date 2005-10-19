package rice.email.proxy.imap.commands;

import rice.email.proxy.imap.ImapState;

import rice.email.proxy.mailbox.MailboxException;


/**
 * UNSUBSCRIBE command.
 * 
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.3.7">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.3.7 </a>
 * </p>
 */
public class UnsubscribeCommand
    extends AbstractImapCommand
{
    String _folder;

    public UnsubscribeCommand()
    {
        super("UNSUBSCRIBE");
    }

    public boolean isValidForState(ImapState state)
    {

        return state.isAuthenticated();
    }

    public void execute()
    {
        try
        {
            getState().getMailbox().unsubscribe(getConn(), getFolder());
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
        this._folder = mailbox;
    }
}
