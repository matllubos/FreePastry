package rice.email.proxy.imap.commands;

import rice.email.proxy.imap.ImapState;


public class BadSyntaxCommand
    extends AbstractImapCommand
{
    public BadSyntaxCommand()
    {
        super("<BAD SYNTAX>");
    }

    public boolean isValidForState(ImapState state)
    {

        return true;
    }

    String _msg;

    public BadSyntaxCommand(String msg)
    {
        this();
        _msg = msg;
    }

    public void execute()
    {
        String message = "BAD syntax";

        if (_msg != null)
            message += ": " + _msg;

        if (getTag() == null)
            untaggedResponse(message);
        else
            taggedResponse(message);
    }
}