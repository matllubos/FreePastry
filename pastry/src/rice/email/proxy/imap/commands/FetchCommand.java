package rice.email.proxy.imap.commands;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import rice.email.proxy.imap.ImapState;
import rice.email.proxy.imap.commands.fetch.FetchOptionRegistry;
import rice.email.proxy.imap.commands.fetch.FetchPart;
import rice.email.proxy.mail.StoredMessage;
import rice.email.proxy.mailbox.MailFolder;
import rice.email.proxy.mailbox.MailboxException;
import rice.email.proxy.mailbox.MsgFilter;


/**
 * FETCH command.
 * 
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.4.5">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.4.5 </a>
 * </p>
 * 
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-7.4.2">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-7.4.2 </a>
 * </p>
 */
public class FetchCommand
    extends AbstractImapCommand
{
	static FetchOptionRegistry regestry = new FetchOptionRegistry();
	
    public FetchCommand()
    {
        super("FETCH");
	appendPartRequest("UID");
    }

    public boolean isValidForState(ImapState state)
    {

        return state.isSelected();
    }

    MsgFilter _range;
    List parts = new LinkedList();

    public void execute()
    {
        try
        {
            ImapState state = getState();
            MailFolder fold = state.getSelectedFolder();
            List msgs = fold.getMessages(_range);
            for (Iterator i = msgs.iterator(); i.hasNext();)
            {
                StoredMessage msg = (StoredMessage) i.next();

                fetchMessage(msg);
            }

            taggedSimpleSuccess();
        }
        catch (MailboxException e)
        {
            taggedExceptionFailure(e);
        }
    }

    void fetchMessage(StoredMessage msg)
               throws MailboxException
    {
        getConn().print("* " + msg.getSequenceNumber() + 
                        " FETCH (");
        for (Iterator i = parts.iterator(); i.hasNext();)
        {
            Object part = i.next();
            FetchPart handler = regestry.getHandler(part);
            handler.setConn(getConn());
            handler.fetch(msg, part);

            if (i.hasNext())
                getConn().print(" ");
        }

        getConn().print(")\r\n");
    }

    public void appendPartRequest(String string)
    {
	if (parts.contains(string)) {
	    return;
	}
      if (string.trim().equalsIgnoreCase("ALL")) {
        parts.add("FLAGS");
        parts.add("INTERNALDATE");
        parts.add("RFC822.SIZE");
        parts.add("ENVELOPE");
      } else {
        parts.add(string.toUpperCase());
      }
    }

    public void appendPartRequest(Object obj)
    {
        parts.add(obj);
    }

    public List getParts()
    {

        return parts;
    }

    public MsgFilter getRange()
    {

        return _range;
    }

    public void setRange(MsgFilter range)
    {
        _range = range;
    }
}



