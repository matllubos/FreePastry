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
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;


public class UserManagerImpl implements UserManager {

  private EmailService email;
  private MailboxManager manager;
  
  public UserManagerImpl(EmailService email, MailboxManager manager) {
    this.email = email;
    this.manager = manager;
  }
  
  public User getUser(String name) throws NoSuchUserException {
    if (email.getPost().getEntityAddress().toString().equals(name)) {
      return new UserImpl(name, manager, "monkey");
    }

    throw new NoSuchUserException("User " + name + " not found!");
  }

  public void createUser(String name, String service, String authenticationData) throws UserException {
    throw new UserException("Cannot create users.");
  }

  public void deleteUser(String name) throws UserException {
    throw new UserException("Cannot delete users.");
  }
}