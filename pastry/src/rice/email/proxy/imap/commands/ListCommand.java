package rice.email.proxy.imap.commands;

import rice.email.proxy.imap.ImapState;

import rice.email.proxy.mailbox.MailFolder;
import rice.email.proxy.mailbox.MailboxException;


/**
 * LIST command.
 * 
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.3.8">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.3.8 </a>
 * </p>
 */
public class ListCommand
    extends AbstractImapCommand
{
    public ListCommand()
    {
        super("LIST");
    }

    public boolean isValidForState(ImapState state)
    {

        return state.isAuthenticated();
    }

    String _folder;
    String reference;

    public void execute()
    {
        if ("".equals(_folder))
        {
            untaggedSimpleResponse("(\\Noselect) \"/\" \"\"");
            taggedSimpleSuccess();

            return;
        }

        try
        {
            MailFolder[] fold = getState().getMailbox().listFolders(
                                        _folder);

            for (int i = 0; i < fold.length; i++)
            {
                untaggedSimpleResponse(
                        "() \"/\" \"" + fold[i].getFullName() + "\"");
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