package rice.email.proxy.imap.commands;

import rice.email.proxy.imap.ImapState;

import rice.email.proxy.mailbox.MailFolder;
import rice.email.proxy.mailbox.MailboxException;
import rice.email.proxy.mailbox.MsgFilter;


/**
 * EXAMINE command.
 * 
 * <p>
 * <a href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.3.2">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.3.2 </a>
 * </p>
 */
public class ExamineCommand
    extends AbstractImapCommand
{
    String _folder;

    public ExamineCommand()
    {
        super("EXAMINE");
    }

    public boolean isValidForState(ImapState state)
    {

        return state.isAuthenticated();
    }

    public ExamineCommand(String name)
    {
        super(name);
    }

    public void execute()
    {
        try
        {
            if (_folder != null) {
              MailFolder fold = getState().getFolder(_folder);

              int exists         = fold.getMessages(MsgFilter.ALL).size();
              int recent         = fold.getMessages(MsgFilter.RECENT).size();
              String UIDvalidity = fold.getUIDValidity();

              untaggedResponse(exists + " EXISTS");
              untaggedResponse(recent + " RECENT");
              untaggedResponse(
                    "FLAGS (\\Answered \\Flagged \\Deleted \\Seen \\Draft)");
	      untaggedSuccess(
		      "[PERMANENTFLAGS (\\* \\Answered \\Flagged \\Deleted \\Seen \\Draft)] Permanant flags");
              untaggedSuccess(
                      "[UIDVALIDITY " + UIDvalidity + 
                      "] UIDs valid ");
            }
          
          String writeStatus = isWritable() ? "[READ-WRITE]" : "[READ-ONLY]";
          
            taggedSuccess(
                    writeStatus + " " + getCmdName() + 
                    " completed");

        }
        catch (MailboxException e)
        {
            taggedExceptionFailure(e);
        }
    }

    protected boolean isWritable()
    {

        return false;
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
