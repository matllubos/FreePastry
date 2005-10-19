package rice.email.proxy.user;

import rice.email.proxy.mailbox.MailboxException;


public interface UserManager {

    User getUser(String name)
          throws NoSuchUserException;

    void createUser(String name, String service, 
                    String authenticationData)
             throws UserException;

    void deleteUser(String name)
             throws UserException;
}