package rice.email.proxy.pop3;

import rice.email.proxy.mailbox.*;
import rice.email.proxy.user.*;


public class Pop3State {
  
  UserManager _manager;
  User _user;
  Mailbox _mailbox;
  MailFolder _inbox;
  String _challenge;
  
  public Pop3State(UserManager manager) {
    _manager = manager;
  }
  
  public String getChallenge() {
    return _challenge;
  }
  
  public void setChallenge(String challenge) {
    this._challenge = challenge;
  }
  
  public User getUser() {
    return _user;
  }
  
  public String getPassword(String username) throws UserException {
    return _manager.getPassword(username);
  }
  
  public User getUser(String username) throws UserException {
    return _manager.getUser(username);
  }
  
  public void setUser(User user) throws MailboxException, UserException {
    _user = user;
  }
  
  public boolean isAuthenticated() {
    return _mailbox != null;
  }
  
  public void authenticate(String pass) throws MailboxException, UserException {
    if (_user == null)
      throw new UserException("No user selected");
    
    _user.authenticate(pass);
    _mailbox = _user.getMailbox();
    _inbox = _mailbox.getFolder("INBOX");
  }
  
  public MailFolder getFolder() {
    return _inbox;
  }
}