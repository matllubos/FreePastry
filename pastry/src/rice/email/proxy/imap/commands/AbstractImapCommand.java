package rice.email.proxy.imap.commands;

import rice.email.proxy.imap.ImapConnection;
import rice.email.proxy.imap.ImapState;

import rice.email.proxy.mailbox.MailboxException;

public abstract class AbstractImapCommand
{
    String _tag;
    ImapConnection _conn;
    ImapState _state;
    final String _cmdName;

    public AbstractImapCommand(String name)
    {
        _cmdName = name;
    }

    void taggedResponse(String s)
    {
        _conn.println(_tag + " " + s);
    }

    protected void taggedSuccess(String s)
    {
        _conn.println(_tag + " OK " + s);
    }

    protected void taggedSimpleSuccess()
    {
        _conn.println(_tag + " OK " + getCmdName() + " completed");
    }

    protected void taggedFailure(String s)
    {
        _conn.println(_tag + " NO " + s);
    }

    protected void taggedSimpleFailure()
    {
        _conn.println(_tag + " NO " + getCmdName() + " failed");
    }

    protected void taggedExceptionFailure(Throwable exception)
    {
        _conn.println("\r\n" +
                _tag + " NO " + getCmdName() + " " + 
                exception.getMessage());
    }

    protected void untaggedResponse(String s)
    {
        _conn.println("* " + s);
    }

    protected void untaggedSuccess(String s)
    {
        _conn.println("* OK " + s);
    }

    protected void untaggedSimpleResponse(String s)
    {
        _conn.println("* " + getCmdName() + " " + s);
    }

    public void setTag(String s)
    {
        _tag = s;
    }

    public String getTag()
    {

        return _tag;
    }

    public abstract boolean isValidForState(ImapState state);

    public abstract void execute();

    public ImapConnection getConn()
    {

        return _conn;
    }

    public void setConn(ImapConnection conn)
    {
        _conn = conn;
    }

    public ImapState getState()
    {

        return _state;
    }

    public void setState(ImapState state)
    {
        _state = state;
    }

    public String getCmdName()
    {

        return _cmdName;
    }
}