package rice.email.proxy.imap.commands;

import rice.email.proxy.imap.ImapState;

import rice.email.proxy.mail.MailException;
import rice.email.proxy.mail.MovingMessage;
import rice.email.proxy.mail.StoredMessage;

import rice.email.proxy.mailbox.MailFolder;
import rice.email.proxy.mailbox.Mailbox;
import rice.email.proxy.mailbox.MailboxException;
import rice.email.proxy.mailbox.MsgFilter;

import java.io.IOException;

import java.util.Iterator;
import java.util.List;


/**
 * COPY command.
 * 
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.4.7">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.4.7 </a>
 * </p>
 */
public class CopyCommand
    extends AbstractImapCommand
{
    public CopyCommand()
    {
        super("COPY");
    }

    public boolean isValidForState(ImapState state)
    {

        return state.isSelected();
    }

    MsgFilter _range;
    String _folder;

    public void execute()
    {
        try
        {
            ImapState state = getState();
            MailFolder fold = state.getSelectedFolder();
            List msgs       = fold.getMessages(_range);
            for (Iterator i = msgs.iterator(); i.hasNext();)
            {
                StoredMessage msg = (StoredMessage) i.next();

                copyMessage(msg, state.getMailbox(), _folder);
            }

            taggedSimpleSuccess();
        }
        catch (MailboxException e)
        {
            taggedExceptionFailure(e);
        }
    }

    void copyMessage(StoredMessage msg, Mailbox box, String destFold)
              throws MailboxException
    {
        MovingMessage iMsg = getState().createMovingMessage();
        try
        {
            iMsg.readFullContent(msg.getMessage().getContents());
            box.getFolder(destFold).put(iMsg);
        }
        catch (IOException ioe)
        {
            throw new MailboxException(ioe);
        }
        catch (MailException me)
        {
            throw new MailboxException(me);
        }
        finally
        {
            iMsg.releaseContent();
        }
    }

    public String getFolder()
    {

        return _folder;
    }

    public MsgFilter getRange()
    {

        return _range;
    }

    public void setFolder(String mailbox)
    {
        _folder = mailbox;
    }

    public void setRange(MsgFilter range)
    {
        _range = range;
    }
}