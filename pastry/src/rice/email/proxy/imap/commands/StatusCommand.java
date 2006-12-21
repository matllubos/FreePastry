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

import rice.email.proxy.mailbox.MailFolder;
import rice.email.proxy.mailbox.MailboxException;
import rice.email.proxy.mailbox.MsgFilter;

import java.util.List;


/**
 * STATUS command.
 * 
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.3.10">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.3.10 </a>
 * </p>
 * 
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-7.2.4">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-7.2.4 </a>
 * </p>
 */
public class StatusCommand
    extends AbstractImapCommand
{
    String _folder;
    List _requests;

    public StatusCommand()
    {
        super("STATUS");
    }

    public boolean isValidForState(ImapState state)
    {

        return state.isAuthenticated();
    }

    public StatusCommand(String name)
    {
        super(name);
    }

    public void execute()
    {
        try
        {
            MailFolder fold = getState().getFolder(_folder);

            getConn().print("* STATUS \"" + _folder + "\" (");
            String response = "";
            if (_requests.contains("MESSAGES")) {
                int exists = fold.getMessages(MsgFilter.ALL).size();
                response += "MESSAGES " + exists + " ";
            }

            if (_requests.contains("RECENT")) {
                int recent = fold.getMessages(MsgFilter.RECENT).size();
                response += "RECENT " + recent + " ";
            }

            if (_requests.contains("UNSEEN")) {
              int recent = fold.getMessages(MsgFilter.NOT(MsgFilter.SEEN)).size();
              response += "UNSEEN " + recent + " ";
            }
            
            if (_requests.contains("UIDVALIDITY")) {
                String uid = fold.getUIDValidity();
                response += "UIDVALIDITY " + uid + " ";
            }

            if (_requests.contains("UIDNEXT")) {
              int uid = fold.getNextUID();
              response += "UIDNEXT " + uid + " ";
            }

            getConn().print(response.trim());

            getConn().print(")\r\n");

            taggedSimpleSuccess();

        }
        catch (MailboxException e)
        {
            taggedExceptionFailure(e);
        }
    }

    public String getFolder()
    {

        return _folder;
    }

    public void setFolder(String mailbox)
    {
        _folder = mailbox;
    }

    public List getRequests()
    {

        return _requests;
    }

    public void setRequests(List requests)
    {
        _requests = requests;
    }
}