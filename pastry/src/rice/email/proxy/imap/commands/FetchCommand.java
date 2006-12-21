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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import rice.email.proxy.imap.ImapState;
import rice.email.proxy.imap.commands.fetch.FetchOptionRegistry;
import rice.email.proxy.imap.commands.fetch.FetchPart;
import rice.email.proxy.mail.StoredMessage;
import rice.email.proxy.mailbox.MailFolder;
import rice.email.proxy.mailbox.MailboxException;
import rice.email.proxy.mailbox.MsgFilter;
import rice.environment.Environment;
import rice.environment.logging.Logger;


/**
* FETCH command.
 *
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.4.5">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.4.5 </a>
 * </p>
 *
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-7.4.2">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-7.4.2 </a>
 * </p>
 */
public class FetchCommand extends AbstractImapCommand {
  
  public static FetchOptionRegistry registry = new FetchOptionRegistry();

  Environment environment;
  
  public FetchCommand(boolean isUID, Environment env) {
    super("FETCH");
    this.isUID = isUID;
    this.environment = env;
  }

  public boolean isValidForState(ImapState state) {
    return state.isSelected();
  }

  boolean isUID;
  MsgFilter _range;
  List parts = new LinkedList();

  public void execute() {
    try {
      if ((isUID) && (! parts.contains("UID")))
        parts.add("UID");
      
      ImapState state = getState();
      MailFolder fold = state.getSelectedFolder();
      List msgs = fold.getMessages(_range);
      for (Iterator i = msgs.iterator(); i.hasNext();) {
        StoredMessage msg = (StoredMessage) i.next();

        getConn().print(fetchMessage(msg));
      }

      taggedSimpleSuccess();
    } catch (MailboxException e) {
      taggedExceptionFailure(e);
    }
  }

  String fetchMessage(StoredMessage msg) {
    try {
      StringBuffer result = new StringBuffer();
      result.append("* ");
      result.append(msg.getSequenceNumber());
      result.append(" FETCH (");
      
      for (Iterator i = parts.iterator(); i.hasNext();) {
        Object part = i.next();
        FetchPart handler = registry.getHandler(part);
        handler.setConn(_conn);
        result.append(handler.fetch(msg, part));
        
        if (i.hasNext())
          result.append(" ");
      }
      
      return result.toString() +")\r\n";
    } catch (MailboxException e) {
      Logger logger = _state.getEnvironment().getLogManager().getLogger(FetchCommand.class, null);
      if (logger.level <= Logger.WARNING) logger.logException(
          "Got exception " + e + " while fetching data - not returning anything.",e);
      return "";
    }
  }

  public void appendPartRequest(String string) {
    if (parts.contains(string)) {
      return;
    }

    parts.add(string.toUpperCase());
  }

  public void appendPartRequest(Object obj)
  {
    parts.add(obj);
  }

  public List getParts()
  {

    return parts;
  }

  public MsgFilter getRange()
  {

    return _range;
  }

  public void setRange(MsgFilter range)
  {
    _range = range;
  }
}













