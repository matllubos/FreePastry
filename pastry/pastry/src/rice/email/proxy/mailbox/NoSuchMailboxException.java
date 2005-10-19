package rice.email.proxy.mailbox;

public class NoSuchMailboxException
    extends MailboxException
{
    public NoSuchMailboxException()
    {
        super();
    }

    public NoSuchMailboxException(String s)
    {
        super(s);
    }

    public NoSuchMailboxException(String s, Throwable e)
    {
        super(s, e);
    }

    public NoSuchMailboxException(Throwable e)
    {
        super(e);
    }
}