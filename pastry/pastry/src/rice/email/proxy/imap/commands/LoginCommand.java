package rice.email.proxy.imap.commands;

import rice.email.proxy.imap.ImapState;
import rice.email.proxy.user.User;
import rice.email.proxy.user.UserException;


/**
 * LOGIN command.
 * 
 * <p>
 * <a href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.2.2">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.2.2 </a>
 * </p>
 */
public class LoginCommand
    extends AbstractImapCommand
{
    String username;
    String password;

    public LoginCommand()
    {
        super("LOGIN");
    }

    public boolean isValidForState(ImapState state)
    {

        return !state.isAuthenticated();
    }

    public void execute()
    {
        try
        {
        	User user = getState().getUser(username);
        	user.authenticate(password);
            getState().setUser(user);
            taggedSimpleSuccess();
        }
        catch (UserException nsue)
        {
            taggedExceptionFailure(nsue);
        }
    }

    public String getUser()
    {

        return username;
    }

    public void setUser(String user)
    {
        username = user;
    }

    public String getPassword()
    {

        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }
}