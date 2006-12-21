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

import rice.email.proxy.mail.StoredMessage;

import rice.email.proxy.mailbox.MailFolder;
import rice.email.proxy.mailbox.MailboxException;
import rice.email.proxy.mailbox.MsgFilter;

import java.util.Iterator;
import java.util.List;


/**
 * EXPUNGE command.
 * 
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.4.3">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.4.3 </a>
 * </p>
 */
public class ExpungeCommand
    extends AbstractImapCommand
{
    public ExpungeCommand()
    {
        super("EXPUNGE");
    }

    public boolean isValidForState(ImapState state)
    {

        return state.isSelected();
    }

    public void execute()
    {
        try
        {
            MailFolder fold = getState().getSelectedFolder();
            List msgs       = fold.getMessages(MsgFilter.DELETED);

            StoredMessage[] messages = (StoredMessage[]) msgs.toArray(new StoredMessage[0]);
            fold.purge(messages);
            
            int numDeleted = 0;
            for (Iterator i = msgs.iterator(); i.hasNext();) {
                StoredMessage msg = (StoredMessage) i.next();
                int msgNum = msg.getSequenceNumber() - numDeleted;
                untaggedResponse(msgNum + " EXPUNGE");
                getState().broadcastUnsolicited(msgNum + " EXPUNGE");
                numDeleted++;
            }

            taggedSimpleSuccess();
        }
        catch (MailboxException me)
        {
            taggedExceptionFailure(me);
        }
    }
}