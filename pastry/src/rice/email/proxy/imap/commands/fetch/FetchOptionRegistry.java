package rice.email.proxy.imap.commands.fetch;

public class FetchOptionRegistry
{
    private static FetchPart[] HANDLERS = new FetchPart[] 
    {
        new BodyPart(), new MessagePropertyPart()
    };
    private static FetchPart NIL_HANDLER = new NilPart();

    public FetchPart getHandler(Object partType)
    {
        for (int i = 0; i < HANDLERS.length; i++)
        {
            FetchPart possibleHandler = HANDLERS[i];
            if (possibleHandler.canHandle(partType))

                return possibleHandler;
        }

        return NIL_HANDLER;
    }
}