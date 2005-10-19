package rice.email.proxy.user;

import rice.email.proxy.mail.MovingMessage;

import rice.email.proxy.mailbox.Mailbox;
import rice.email.proxy.mailbox.MailboxException;


public interface User
{
    String getName();

    String getMailboxType();

    Mailbox getMailbox()
                throws UserException;

    void deliver(MovingMessage msg)
          throws UserException;

    void create()
         throws UserException;

    void delete()
         throws UserException;

    String getAuthenticationData();

    void authenticate(String password)
               throws UserException;
}