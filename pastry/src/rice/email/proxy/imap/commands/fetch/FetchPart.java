package rice.email.proxy.imap.commands.fetch;

import rice.email.proxy.imap.ImapConnection;

import rice.email.proxy.mail.StoredMessage;


public abstract class FetchPart
{
    ImapConnection _conn;

    public abstract boolean canHandle(Object req);

    public abstract void fetch(StoredMessage msg, Object part);

    public ImapConnection getConn()
    {

        return _conn;
    }

    public void setConn(ImapConnection conn)
    {
        _conn = conn;
    }
}