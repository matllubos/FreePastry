package rice.email.proxy.mail;

import rice.email.proxy.mailbox.*;

public interface StoredMessage
{
  int getUID();

  int getSequenceNumber();

  MimeMessage getMessage()
    throws MailboxException;

  FlagList getFlagList();

  void purge()
    throws MailboxException; 
}