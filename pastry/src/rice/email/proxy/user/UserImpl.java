package rice.email.proxy.user;

import rice.email.proxy.mail.MovingMessage;

import rice.email.proxy.mailbox.Mailbox;
import rice.email.proxy.mailbox.MailboxException;
import rice.email.proxy.mailbox.MailboxManager;

import java.io.Serializable;

public class UserImpl implements User, Serializable {
  MailboxManager _manager;
  String _name;
  String _pass;

  public UserImpl(String name, MailboxManager manager, String password) {
    _name = name;
    _manager = manager;
    _pass = password;
  }

  public void create() throws UserException {
    try {
      _manager.createMailbox(getName());
    } catch (MailboxException me) {
      throw new UserException(me);
    }
  }

  public void delete() throws UserException {
    try {
      _manager.destroyMailbox(getName());
    } catch (MailboxException me) {
      throw new UserException(me);
    }
  }

  public void deliver(MovingMessage msg) throws UserException {
    try {
      _manager.getMailbox(_name).put(msg);
    } catch (MailboxException me) {
      throw new UserException(me);
    }
  }

  public String getMailboxType() {

    return _manager.getMailboxType();
  }

  public Mailbox getMailbox() throws UserException {
    try {
      return _manager.getMailbox(_name);
    } catch (MailboxException me) {
      throw new UserException(me);
    }
  }

  public String getName() {
    return _name;
  }

  public String getAuthenticationData() {
    return _pass;
  }

  public void authenticate(String pass) throws UserException {
    if (!_pass.equals(pass)) throw new UserException("Invalid password");
  }
}