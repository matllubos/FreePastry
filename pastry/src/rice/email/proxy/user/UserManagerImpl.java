package rice.email.proxy.user;

import rice.email.*;
import rice.email.proxy.mailbox.MailboxManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;


public class UserManagerImpl implements UserManager {

  private EmailService email;
  private MailboxManager manager;
  private Hashtable users;
  
  public UserManagerImpl(EmailService email, MailboxManager manager) {
    this.email = email;
    this.manager = manager;
    this.users = new Hashtable();
  }
  
  public String getPassword(String name) throws NoSuchUserException {
    if (users.get(name) != null) {
      return (String) users.get(name);
    }
    
    throw new NoSuchUserException("User " + name + " not found!");
  }
  
  public User getUser(String name) throws NoSuchUserException {
    if (users.get(name) != null) {
      return new UserImpl(name, manager, (String) users.get(name));
    }

    throw new NoSuchUserException("User " + name + " not found!");
  }

  public void createUser(String name, String service, String authenticationData) throws UserException {
    users.put(name, authenticationData);
  }

  public void deleteUser(String name) throws UserException {
    throw new UserException("Cannot delete users.");
  }
}