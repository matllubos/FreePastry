package rice.email.proxy.web;

import rice.email.proxy.mailbox.*;
import rice.email.proxy.mail.MovingMessage;
import rice.email.proxy.user.*;
import rice.email.proxy.util.*;

public class WebState {
  
  protected User user;
  protected MailFolder currentFolder;
  protected int currentMessageUID;
  protected UserManager userManager;
  
  public WebState(UserManager userManager) {
    this.userManager = userManager;
    this.currentMessageUID = -1;
  }

  public User getUser() {
    return user;
  }
  
  public MailFolder getCurrentFolder() {
    return currentFolder;
  }
  
  public int getCurrentMessageUID() {
    return currentMessageUID;
  }
  
  public void setCurrentMessageUID(int uid) {
    this.currentMessageUID = uid;
  }
  
  public void setCurrentFolder(String name) throws UserException, MailboxException {
    this.currentFolder = user.getMailbox().getFolder(name);
    this.currentMessageUID = -1;
  }
  
  public void setUser(User user) throws UserException, MailboxException {
    this.user = user;
    this.currentFolder = user.getMailbox().getFolder("INBOX");
  } 
  
  public User getUser(String username) throws UserException  {
    return userManager.getUser(username);
  }
  
  public String getPassword(String username) throws UserException {
    return userManager.getPassword(username);
  }
  
}