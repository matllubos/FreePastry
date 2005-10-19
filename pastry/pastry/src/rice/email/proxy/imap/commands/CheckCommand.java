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
public class CheckCommand
extends AbstractImapCommand
{
  public CheckCommand()
{
    super("CHECK");
}

public boolean isValidForState(ImapState state)
{

  return true;
}

public void execute()
{
  taggedSimpleSuccess();
}
}