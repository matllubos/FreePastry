package rice.email.proxy.test.user;

import rice.email.proxy.mail.MovingMessage;

import rice.email.proxy.mailbox.*;
import rice.email.proxy.test.mailbox.*;
import rice.email.proxy.user.*;


public class MockUser
    implements User
{
    MockMailboxManager _manager;
    String _name;

    public MockUser(String name, MockMailboxManager manager)
    {
        _name = name;
        _manager = manager;

    }

    public void create()
    {
        _manager.createMailbox(_name);
    }

    public void delete()
    {
        _manager.destroyMailbox(_name);
    }

    public void deliver(MovingMessage msg)
                 throws UserException
    {
        try
        {
            getMailbox().put(msg);
        }
        catch (MailboxException me)
        {
            throw new UserException(me);
        }
    }

    public Mailbox getMailbox()
                       throws UserException
    {
        try
        {

            return _manager.getMailbox(_name);
        }
        catch (NoSuchMailboxException nsme)
        {
            throw new UserException(nsme);
        }
    }

    public String getMailboxType()
    {

        return _manager.getMailboxType();
    }

    public String getName()
    {

        return _name;
    }

    public String getAuthenticationData()
    {

        return "";
    }

    public void authenticate(String password)
    {

        // all passwords are good
    }
}