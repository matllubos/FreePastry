package rice.email.proxy.mailbox;

public class MailboxException
    extends Exception
{
    public MailboxException()
    {
        super();
    }

    public MailboxException(String s)
    {
        super(s);
    }

    public MailboxException(String s, Throwable e)
    {
        super(s, e);
    }

    public MailboxException(Throwable e)
    {
        super(e);
    }
}