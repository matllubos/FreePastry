package rice.email.proxy.imap.commands;

import rice.email.proxy.imap.ImapState;

import rice.email.proxy.mailbox.MailFolder;
import rice.email.proxy.mailbox.MailboxException;
import rice.email.proxy.mailbox.MsgFilter;

import java.util.List;


/**
 * STATUS command.
 * 
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.3.10">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.3.10 </a>
 * </p>
 * 
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-7.2.4">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-7.2.4 </a>
 * </p>
 */
public class StatusCommand
    extends AbstractImapCommand
{
    String _folder;
    List _requests;

    public StatusCommand()
    {
        super("STATUS");
    }

    public boolean isValidForState(ImapState state)
    {

        return state.isAuthenticated();
    }

    public StatusCommand(String name)
    {
        super(name);
    }

    public void execute()
    {
        try
        {
            MailFolder fold = getState().getFolder(_folder);

            getConn().print("* STATUS \"" + _folder + "\" (");
            String response = "";
            if (_requests.contains("MESSAGES")) {
                int exists = fold.getMessages(MsgFilter.ALL).size();
                response += "MESSAGES " + exists + " ";
            }

            if (_requests.contains("RECENT")) {
                int recent = fold.getMessages(MsgFilter.RECENT).size();
                response += "RECENT " + recent + " ";
            }

            if (_requests.contains("UNSEEN")) {
              int recent = fold.getMessages(MsgFilter.NOT(MsgFilter.SEEN)).size();
              response += "UNSEEN " + recent + " ";
            }
            
            if (_requests.contains("UIDVALIDITY")) {
                String uid = fold.getUIDValidity();
                response += "UIDVALIDITY " + uid + " ";
            }

            if (_requests.contains("UIDNEXT")) {
              int uid = fold.getNextUID();
              response += "UIDNEXT " + uid + " ";
            }

            getConn().print(response.trim());

            getConn().print(")\r\n");

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

    public List getRequests()
    {

        return _requests;
    }

    public void setRequests(List requests)
    {
        _requests = requests;
    }
}