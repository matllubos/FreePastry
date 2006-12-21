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

import rice.email.proxy.imap.ImapState;

import rice.email.proxy.mail.*;

import rice.email.proxy.mailbox.FlagList;
import rice.email.proxy.mailbox.Mailbox;
import rice.email.proxy.mailbox.MailboxException;

import rice.email.proxy.util.StreamUtils;
import rice.environment.Environment;

import java.io.IOException;

import java.util.List;


/**
 * APPEND command.
 * 
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.3.11">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.3.11 </a>
 * </p>
 */
public class AppendCommand
    extends AbstractImapCommand
{
  
    public AppendCommand()
    {
        super("APPEND");
    }

    public boolean isValidForState(ImapState state)
    {

        return state.isAuthenticated();
    }

    List _flags;
    String _date;
    String _folder;
    int _len         = -1;
    IOException _ioe;

    public void execute()
    {
        getConn().println("+ Ready for data");
        MovingMessage msg = getState().createMovingMessage();
        try
        {
            msg.readFullContent(StreamUtils.limit(getConn().getReader(),  _len));

            // skip CRLF
            getConn().readLine();
           
            long internaldate = _state.getEnvironment().getTimeSource().currentTimeMillis();
            
            try {
              internaldate = MimeMessage.dateWriter.parse(_date).getTime();
            } catch (Exception e) {
              // do nothing - revert to current date/time
            }
            
            Mailbox box = getState().getMailbox();
            box.getFolder(_folder).put(msg, _flags, internaldate);
            taggedSimpleSuccess();
        }
        catch (MailboxException me)
        {
            taggedExceptionFailure(me);
        }
        catch (IOException ioe)
        {
            taggedExceptionFailure(new MailboxException(_ioe));
        }
        finally
        {
            msg.releaseContent();
        }
    }

    public String getDate()
    {

        return _date;
    }

    public List getFlags()
    {

        return _flags;
    }

    public void setContentLength(int len)
    {
        _len = len;
    }

    public void setDate(String date)
    {
        _date = date;
    }

    public void setFlags(List flags)
    {
        _flags = flags;
    }

    public String getFolder()
    {

        return _folder;
    }

    public void setFolder(String mailbox)
    {
        _folder = mailbox;
    }
}
