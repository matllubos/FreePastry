package rice.email.proxy.imap.commands.search;

import rice.email.proxy.imap.ImapConnection;
import rice.email.proxy.mail.StoredMessage;
import rice.email.proxy.mailbox.*;

public class FilterSearchPart extends SearchPart {

  MsgFilter filter;

  public FilterSearchPart(MsgFilter filter) {
    this.filter = (MsgFilter) filter;
  }

  public boolean includes(StoredMessage msg) {
    return filter.includes(msg);
  }
}