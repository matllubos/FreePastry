package rice.email.proxy.imap.commands;

import rice.email.proxy.imap.ImapState;

import rice.email.proxy.mail.*;

import rice.email.proxy.mailbox.FlagList;
import rice.email.proxy.mailbox.Mailbox;
import rice.email.proxy.mailbox.MailboxException;

import rice.email.proxy.util.StreamUtils;

import java.io.IOException;

import java.util.List;


/**
 * APPEND command.
 * 
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.3.11">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.3.11 </a>
 * </p>
 */
public class AppendCommand
    extends AbstractImapCommand
{
    public AppendCommand()
    {
        super("APPEND");
    }

    public boolean isValidForState(ImapState state)
    {

        return state.isAuthenticated();
    }

    List _flags;
    String _date;
    String _folder;
    int _len         = -1;
    IOException _ioe;

    public void execute()
    {
        getConn().println("+ Ready for data");
        MovingMessage msg = getState().createMovingMessage();
        try
        {
            msg.readFullContent(StreamUtils.limit(getConn().getReader(),  _len));

            // skip CRLF
            getConn().readLine();
          
            long internaldate = System.currentTimeMillis();
            
            try {
              internaldate = MimeMessage.dateWriter.parse(_date).getTime();
            } catch (Exception e) {
              // do nothing - revert to current date/time
            }
            
            Mailbox box = getState().getMailbox();
            box.getFolder(_folder).put(msg, _flags, internaldate);
            taggedSimpleSuccess();
        }
        catch (MailboxException me)
        {
            taggedExceptionFailure(me);
        }
        catch (IOException ioe)
        {
            taggedExceptionFailure(new MailboxException(_ioe));
        }
        finally
        {
            msg.releaseContent();
        }
    }

    public String getDate()
    {

        return _date;
    }

    public List getFlags()
    {

        return _flags;
    }

    public void setContentLength(int len)
    {
        _len = len;
    }

    public void setDate(String date)
    {
        _date = date;
    }

    public void setFlags(List flags)
    {
        _flags = flags;
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
