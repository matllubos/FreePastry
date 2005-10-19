package rice.email.proxy.imap.commands.search;

import java.util.*;

import rice.email.proxy.imap.ImapConnection;
import rice.email.proxy.mail.StoredMessage;
import rice.email.proxy.mailbox.*;

public class AndSearchPart extends SearchPart {

  List parts = new ArrayList();

  public boolean includes(StoredMessage msg) {
    Iterator i = getArguments().iterator();

    while (i.hasNext()) {
      if (! ((SearchPart) i.next()).includes(msg)) {
        return false;
      }
    }

    return true;
  }

  public void addArgument(SearchPart part) {
    parts.add(part);
  }

  public List getArguments() {
    return parts;
  }
}