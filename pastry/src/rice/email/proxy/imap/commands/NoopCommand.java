package rice.email.proxy.imap.commands;

import rice.email.proxy.imap.ImapState;


/**
 * NOOP command.
 * 
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.1.2">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.1.2 </a>
 * </p>
 */
public class NoopCommand
  extends AbstractImapCommand
{
    public NoopCommand()
    {
        super("NOOP");
    }

    public boolean isValidForState(ImapState state)
    {
        return true;
    }

    public void execute() {
        taggedSimpleSuccess();
//        setFolder(getState().getSelectedFolder().getFullName());
//        super.execute();
    }

    protected boolean isWritable() {

        return true;
    }
}