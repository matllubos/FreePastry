package rice.email.proxy.test.user;

import rice.email.proxy.mailbox.*;
import rice.email.proxy.test.mailbox.*;
import rice.email.proxy.user.*;

import java.util.*;


public class MockUserManager
    implements UserManager
{
    Map users = new HashMap();
    MockMailboxManager manager = new MockMailboxManager();

    public void createUser(String name, String service, 
                           String authData)
                    throws UserException
    {
        User user = new MockUser(name, manager);
        user.create();
        users.put(name, user);
    }

    public void deleteUser(String name)
                    throws UserException
    {
        User user = (User) users.remove(name);
        if (user != null)
            user.delete();
    }

    public String getPassword(String name) {
      return "";
    }

    
    public User getUser(String name)
                 throws NoSuchUserException
    {
        User value = (User) users.get(name);
        if (value == null)
            throw new NoSuchUserException("No such user: " + name);

        return value;
    }
}