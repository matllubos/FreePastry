package rice.email.proxy.imap.commands;

import rice.email.proxy.imap.ImapState;


/**
 * CAPABILITY command.
 * 
 * <p>
 * No capabilities are listed at the moment.
 * </p>
 * 
 * <p>
 * See specs at  <a
 * href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-7.2.1">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-7.2.1 </a>
 * </p>
 */
public class CapabilityCommand
    extends AbstractImapCommand
{
    public CapabilityCommand()
    {
        super("CAPABILITY");
    }

    public boolean isValidForState(ImapState state)
    {

        return true;
    }

    public void execute()
    {
        getConn().print("* CAPABILITY IMAP4rev1\r\n");
        taggedSuccess("CAPABILITY completed");
    }
}
