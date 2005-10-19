package rice.email.proxy.mail;

public class MalformedAddressException
    extends Exception
{
    public MalformedAddressException()
    {
        super();
    }

    public MalformedAddressException(String s)
    {
        super(s);
    }

    public MalformedAddressException(String s, Throwable t)
    {
        super(s, t);
    }

    public MalformedAddressException(Throwable t)
    {
        super(t);
    }
}