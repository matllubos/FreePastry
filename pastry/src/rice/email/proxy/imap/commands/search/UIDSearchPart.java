package rice.email.proxy.imap.commands.search;

import rice.email.proxy.imap.ImapConnection;
import rice.email.proxy.mail.StoredMessage;
import rice.email.proxy.mailbox.*;

public class UIDSearchPart extends SearchPart {

  MsgFilter filter;

  public UIDSearchPart(MsgFilter filter) {
    this.filter = (MsgFilter) filter;
  }

  public boolean includes(StoredMessage msg) {
    return filter.includes(msg);
  }
}