/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/
package rice.email.proxy.imap.commands;

import rice.email.proxy.imap.ImapConnection;
import rice.email.proxy.imap.ImapState;

import rice.email.proxy.mailbox.MailboxException;
import rice.environment.logging.Logger;

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
        _state.printUnsolicited(_conn);
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
      _conn.println(_tag + " BAD " + exception.getMessage());
      Logger logger = _state.getEnvironment().getLogManager().getLogger(getClass(), null);
      if (logger.level <= Logger.SEVERE) logger.logException(
          "SEVERE: Exception " + exception + " occurred while attempting to perform IMAP task " + _tag + " " + _cmdName, exception);
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