package rice.email.proxy.imap.commands;

import rice.email.proxy.imap.ImapState;

import rice.email.proxy.mail.StoredMessage;

import rice.email.proxy.mailbox.MailFolder;
import rice.email.proxy.mailbox.MailboxException;
import rice.email.proxy.mailbox.MsgFilter;

import java.util.Iterator;
import java.util.List;


/**
 * EXPUNGE command.
 * 
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.4.3">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.4.3 </a>
 * </p>
 */
public class ExpungeCommand
    extends AbstractImapCommand
{
    public ExpungeCommand()
    {
        super("EXPUNGE");
    }

    public boolean isValidForState(ImapState state)
    {

        return state.isSelected();
    }

    public void execute()
    {
        try
        {
            MailFolder fold = getState().getSelectedFolder();
            List msgs       = fold.getMessages(MsgFilter.DELETED);

            StoredMessage[] messages = (StoredMessage[]) msgs.toArray(new StoredMessage[0]);
            fold.purge(messages);
            
            int numDeleted = 0;
            for (Iterator i = msgs.iterator(); i.hasNext();) {
                StoredMessage msg = (StoredMessage) i.next();
                int msgNum = msg.getSequenceNumber() - numDeleted;
                untaggedResponse(msgNum + " EXPUNGE");
                getState().broadcastUnsolicited(msgNum + " EXPUNGE");
                numDeleted++;
            }

            taggedSimpleSuccess();
        }
        catch (MailboxException me)
        {
            taggedExceptionFailure(me);
        }
    }
}