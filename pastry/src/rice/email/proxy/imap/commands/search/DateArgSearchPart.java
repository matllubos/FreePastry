package rice.email.proxy.imap.commands.search;

import rice.email.proxy.imap.ImapConnection;
import rice.email.proxy.mail.StoredMessage;
import rice.email.proxy.mailbox.*;

public class DateArgSearchPart extends SearchPart {

  String argument;

  public boolean includes(StoredMessage msg) {
    // XXX NEED TO IMPLEMENT
    return false;
  }

  public void setArgument(String argument) {
    this.argument = argument;
  }

  public String getArgument() {
    return argument;
  }
}