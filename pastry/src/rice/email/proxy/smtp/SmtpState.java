package rice.email.proxy.smtp;

import rice.email.proxy.mail.MovingMessage;
import rice.email.proxy.user.*;
import rice.email.proxy.util.*;

public class SmtpState {
  UserManager userManager;
  MovingMessage currentMessage;
  Workspace _workspace;
  User user;

  public SmtpState(Workspace workspace, UserManager manager) {
    _workspace = workspace;
    this.userManager = manager;
    clearMessage();
  }

  public MovingMessage getMessage() {
    return currentMessage;
  }

  /**
    * To destroy a half-contructed message.
   */
  public void clearMessage() {
    if (currentMessage != null)
      currentMessage.releaseContent();

    currentMessage = new MovingMessage(_workspace);
  }
  
  public User getUser(String username) throws UserException  {
    return userManager.getUser(username);
  }
  
  public User getUser() {
    return user;
  }
  
  public String getPassword(String username) throws UserException {
    return userManager.getPassword(username);
  }
  
  public void setUser(User user) throws UserException {
    this.user = user;
  }
}