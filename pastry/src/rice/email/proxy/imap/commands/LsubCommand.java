package rice.email.proxy.imap.commands;

import rice.email.proxy.imap.ImapState;

import rice.email.proxy.mailbox.MailboxException;


/**
 * LSUB command.
 * 
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.3.9">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.3.9 </a>
 * </p>
 * 
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-7.2.3">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-7.2.3 </a>
 * </p>
 */
public class LsubCommand
    extends AbstractImapCommand
{
    public LsubCommand()
    {
        super("LSUB");
    }

    public boolean isValidForState(ImapState state)
    {

        return state.isAuthenticated();
    }

    String _folder;
    String reference;

    public void execute()
    {
        try
        {
            String[] fold = getState().getMailbox().listSubscriptions(getConn(), _folder);

            for (int i = 0; i < fold.length; i++)
            {
                untaggedSimpleResponse("() NIL \"" + fold[i] + "\"");
            }

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

    public String getReference()
    {

        return reference;
    }

    public void setReference(String reference)
    {
        this.reference = reference;
    }
}









