package rice.email.proxy.imap.commands.search;

import rice.email.proxy.imap.ImapConnection;
import rice.email.proxy.mail.StoredMessage;
import rice.email.proxy.mailbox.*;

public class NotSearchPart extends SearchPart {

  SearchPart part;

  public NotSearchPart(SearchPart part) {
    this.part = part;
  }

  public boolean includes(StoredMessage msg) {
    return (! part.includes(msg));
  }
}