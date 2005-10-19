package rice.email.proxy.mail;

import rice.email.*;
import rice.email.proxy.mailbox.*;

public interface StoredMessage
{
  int getUID();

  int getSequenceNumber();

  Email getMessage()
    throws MailboxException;

  FlagList getFlagList();

  void purge()
    throws MailboxException; 
}