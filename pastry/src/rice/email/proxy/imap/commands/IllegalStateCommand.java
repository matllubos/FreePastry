package rice.email.proxy.imap.commands;

import rice.email.proxy.imap.ImapState;


public class IllegalStateCommand
    extends AbstractImapCommand
{
    public IllegalStateCommand()
    {
        super("<ILLEGAL STATE>");
    }

    public boolean isValidForState(ImapState state)
    {

        return true;
    }

    public void execute()
    {
        String message = "BAD illegal state";

        
            taggedResponse(message);
    }
}