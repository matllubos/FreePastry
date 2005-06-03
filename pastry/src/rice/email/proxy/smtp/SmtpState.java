package rice.email.proxy.smtp;

import java.net.*;

import rice.email.proxy.mail.MovingMessage;
import rice.email.proxy.user.*;
import rice.email.proxy.util.*;
import rice.environment.Environment;

public class SmtpState {
  UserManager userManager;
  MovingMessage currentMessage;
  Workspace _workspace;
  User user;
  InetAddress remote;
  Environment environment;

  public SmtpState(Workspace workspace, UserManager manager, Environment env) {
    _workspace = workspace;
    this.environment = env;
    this.userManager = manager;
    clearMessage();
  }

  public Environment getEnvironment() {
    return environment; 
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
  
  public InetAddress getRemote() {
    return remote;
  }
  
  public void setRemote(InetAddress remote) {
    this.remote = remote;
  }
  
  public String getPassword(String username) throws UserException {
    return userManager.getPassword(username);
  }
  
  public void setUser(User user) throws UserException {
    this.user = user;
  }
}