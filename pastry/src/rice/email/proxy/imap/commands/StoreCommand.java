package rice.email.proxy.imap.commands;

import rice.email.proxy.imap.ImapState;

import rice.email.proxy.mail.StoredMessage;

import rice.email.proxy.mailbox.FlagList;
import rice.email.proxy.mailbox.MailFolder;
import rice.email.proxy.mailbox.MailboxException;
import rice.email.proxy.mailbox.MsgFilter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * STORE command.
 * 
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.4.6">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.4.6 </a>
 * </p>
 */
public class StoreCommand
    extends AbstractImapCommand
{
    public StoreCommand()
    {
        super("STORE");
    }

    public boolean isValidForState(ImapState state)
    {

        return state.isSelected();
    }

    MsgFilter _range;
    List _flags  = new ArrayList();
    String _type;

    public void execute()
    {
        try
        {
            ImapState state = getState();
            MailFolder fold = state.getSelectedFolder();
            List msgs = fold.getMessages(_range);

            boolean silent = _type.endsWith("SILENT");
            boolean add    = _type.startsWith("+");

            System.out.println("Monkey - " + _type + " " + msgs.size());
            
            for (Iterator i = msgs.iterator(); i.hasNext();)
            {
                StoredMessage msg = (StoredMessage) i.next();

                storeMessage(msg, add, !silent);
            }

            taggedSimpleSuccess();
        }
        catch (MailboxException e)
        {
            taggedExceptionFailure(e);
        }
    }

    void storeMessage(StoredMessage msg, boolean set, boolean print)
               throws MailboxException
    {
        FlagList flags = msg.getFlagList();

      System.out.println("Found message " + msg + " with list " + flags);

        for (Iterator i = _flags.iterator(); i.hasNext();)
        {
            String flag = (String) i.next();
            if (set)
                flags.addFlag(flag);
            else
                flags.removeFlag(flag);

            System.out.println("Found flag " + flag);
        }

        flags.commit();

        if (print)
            untaggedResponse(
                    msg.getSequenceNumber() + " FETCH (FLAGS " + 
                    flags.toFlagString() + ")");
    }

    public void setFlags(List flags)
    {
        _flags = flags;
    }

    public List getFlags()
    {

        return _flags;
    }

    public MsgFilter getRange()
    {

        return _range;
    }

    public void setRange(MsgFilter range)
    {
        _range = range;
    }

    public String getType()
    {

        return _type;
    }

    public void setType(String type)
    {
        _type = type;
    }
}