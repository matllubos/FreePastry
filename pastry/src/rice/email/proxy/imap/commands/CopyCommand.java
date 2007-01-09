/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
package rice.email.proxy.imap.commands;

import rice.email.proxy.imap.ImapState;

import rice.email.proxy.mail.MailException;
import rice.email.proxy.mail.MovingMessage;
import rice.email.proxy.mail.StoredMessage;

import rice.email.proxy.mailbox.FlagList;
import rice.email.proxy.mailbox.MailFolder;
import rice.email.proxy.mailbox.Mailbox;
import rice.email.proxy.mailbox.MailboxException;
import rice.email.proxy.mailbox.MsgFilter;

import java.io.IOException;

import java.util.*;


/**
 * COPY command.
 * 
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.4.7">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.4.7 </a>
 * </p>
 */
public class CopyCommand extends AbstractImapCommand {
  
  public CopyCommand() {
    super("COPY");
  }
  
  public boolean isValidForState(ImapState state) {
    return state.isSelected();
  }
  
  MsgFilter _range;
  String _folder;
  
  public void execute() {
    try {
      ImapState state = getState();
      MailFolder fold = state.getSelectedFolder();
      List msgs       = fold.getMessages(_range);
      
      MovingMessage[] messages = new MovingMessage[msgs.size()];
      List[] flags = new List[msgs.size()];
      long[] internaldates = new long[msgs.size()];
      int j=0;
      
      for (Iterator i = msgs.iterator(); i.hasNext();) {
        StoredMessage msg = (StoredMessage) i.next();
        messages[j] = new MovingMessage(msg.getMessage());
        flags[j] = msg.getFlagList().getFlags();
        internaldates[j] = msg.getInternalDate();
        j++;
      }
      
      state.getMailbox().getFolder(_folder).copy(messages, flags, internaldates);
      
      taggedSimpleSuccess();
    } catch (MailboxException e) {
      taggedExceptionFailure(e);
    }
  }
  
  public String getFolder() {
    return _folder;
  }
  
  public MsgFilter getRange() {
    return _range;
  }
  
  public void setFolder(String mailbox) {
    _folder = mailbox;
  }
  
  public void setRange(MsgFilter range) {
    _range = range;
  }
}