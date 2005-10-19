package rice.email.proxy.imap.commands.search;

import rice.email.proxy.imap.ImapConnection;
import rice.email.proxy.mail.StoredMessage;
import rice.email.proxy.mailbox.*;

public class OrSearchPart extends SearchPart {

  SearchPart part1;
  SearchPart part2;

  public OrSearchPart(SearchPart part1, SearchPart part2) {
    this.part1 = part1;
    this.part2 = part2;
  }

  public boolean includes(StoredMessage msg) {
    return (part1.includes(msg) || part2.includes(msg));
  }
}