package rice.email.proxy.imap.commands;

import rice.email.proxy.imap.ImapState;

import rice.email.proxy.mailbox.MailboxException;


/**
 * SUBSCRIBE command.
 * 
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.3.6">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.3.6 </a>
 * </p>
 */
public class SubscribeCommand
    extends AbstractImapCommand
{
    String _folder;

    public SubscribeCommand()
    {
        super("SUBSCRIBE");
    }

    public boolean isValidForState(ImapState state)
    {

        return state.isAuthenticated();
    }

    public void execute()
    {
        try
        {
            getState().getMailbox().subscribe(getFolder());
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