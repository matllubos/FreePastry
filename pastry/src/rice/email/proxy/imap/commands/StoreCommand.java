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

import rice.email.proxy.mail.StoredMessage;

import rice.email.proxy.mailbox.FlagList;
import rice.email.proxy.mailbox.MailFolder;
import rice.email.proxy.mailbox.MailboxException;
import rice.email.proxy.mailbox.MsgFilter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;


/**
 * STORE command.
 * 
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.4.6">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.4.6 </a>
 * </p>
 */
public class StoreCommand extends AbstractImapCommand {
  
  public StoreCommand() {
    super("STORE");
  }
  
  public boolean isValidForState(ImapState state) {
    return state.isSelected();
  }
  
  MsgFilter _range;
  List _flags  = new ArrayList();
  String _type;
  
  public void execute() {
    try {
      if (_flags.contains(FlagList.RECENT_FLAG))
        throw new MailboxException("Attempt to change \\Recent flag");
      
      ImapState state = getState();
      MailFolder fold = state.getSelectedFolder();
      List msgs = fold.getMessages(_range);
      
      boolean silent = _type.endsWith("SILENT");
      boolean add    = _type.startsWith("+");
      Vector updated = new Vector();
      Vector responses = new Vector();
      
      for (Iterator i = msgs.iterator(); i.hasNext();) {
        StoredMessage msg = (StoredMessage) i.next();
        
        if (storeMessage(msg, add))
          updated.add(msg);
        
        responses.add(msg.getSequenceNumber() + " FETCH (FLAGS " + msg.getFlagList().toFlagString() + ")");
      }
      
      StoredMessage[] messages = (StoredMessage[]) updated.toArray(new StoredMessage[0]);
      
      if (messages.length > 0) 
        fold.update(messages);
      
      for (int i=0; i<responses.size(); i++) {
        if (! silent) 
          untaggedResponse((String) responses.elementAt(i));
      
        getState().broadcastUnsolicited((String) responses.elementAt(i));
      }
        
      taggedSimpleSuccess();
    } catch (MailboxException e) {
      taggedExceptionFailure(e);
    }
  }
  
  boolean storeMessage(StoredMessage msg, boolean set) throws MailboxException {
    FlagList flags = msg.getFlagList();
    boolean changed = false;
    
    for (Iterator i = _flags.iterator(); i.hasNext();) {
      String flag = (String) i.next();
      
      if ((set) && (! flags.isSet(flag))) {
        flags.setFlag(flag, true);
        changed = true;
      } else if ((! set) && (flags.isSet(flag))) {
        flags.setFlag(flag, false);
        changed = true;
      }
    }
    
    return changed;
  }
  
  public void setFlags(List flags) {
    _flags = flags;
  }
  
  public List getFlags() {
    return _flags;
  }
  
  public MsgFilter getRange() {
    return _range;
  }
  
  public void setRange(MsgFilter range) {
    _range = range;
  }
  
  public String getType() {
    return _type;
  }
  
  public void setType(String type) {
    _type = type;
  }
}