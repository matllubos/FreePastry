package rice.email.proxy.imap.commands.search;

import rice.email.proxy.imap.ImapConnection;
import rice.email.proxy.mail.StoredMessage;
import rice.email.proxy.mailbox.*;


public abstract class SearchPart extends MsgFilter {

  String type;

  public void setType(String type) {
    this.type = type;
  }

  public String getType() {
    return type;
  }
  
}