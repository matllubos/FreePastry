package rice.email.proxy.imap;

import rice.email.proxy.mail.MovingMessage;

import rice.email.proxy.mailbox.*;

import rice.email.proxy.user.*;

import rice.email.proxy.util.Workspace;


/**
 * Holds current session state.
 * 
 * <p>
 * TODO:
 * </p>
 * 
 * <p>
 * At the moment, the only state information is the current Mailbox
 * and MailFolder. Some of the methods (enterMailbox/Folder) are not
 * state related.  In other words, state management and mailbox
 * access are going to need some major changes to be clean and work
 * correctly.
 * </p>
 * 
 * <p>
 * I'm thinking that the MailboxManager should probably be accessed
 * directly by commands, so this gets cleared out to contain nothing
 * but actual state data.
 * </p>
 */
public class ImapState
{
    UserManager _manager;
    User _user;
    Mailbox _box;
    MailFolder _folder;
    boolean _authenticated;
    boolean _selected;
    boolean _loggedOut;
    Workspace _workspace;

    public ImapState(UserManager man, Workspace workspace)
    {
        _manager = man;
        _workspace = workspace;
    }

    public MovingMessage createMovingMessage()
    {

        return new MovingMessage(_workspace);
    }

    public User getUser(String username)
                 throws UserException
    {

        return _manager.getUser(username);
    }

    public User getUser()
    {

        return _user;
    }

    public void setUser(User user)
                 throws UserException
    {
        _box = user.getMailbox();
        _user = user;
        _authenticated = true;
    }

    public void enterFolder(String fold)
                     throws MailboxException
    {
        _folder = _box.getFolder(fold);
        _selected = true;
    }

    public Mailbox getMailbox()
    {

        return _box;
    }

    public MailFolder getFolder(String fold)
                         throws MailboxException
    {

        return _box.getFolder(fold);
    }

    public MailFolder getSelectedFolder()
    {

        return _folder;
    }

    public boolean isAuthenticated()
    {

        return _authenticated && !_loggedOut;
    }

    public boolean isSelected()
    {

        return _selected && !_loggedOut;
    }

    public void unselect()
    {
        _selected = false;
        _folder = null;
    }

    public void logout()
    {
        _loggedOut = true;
    }
}