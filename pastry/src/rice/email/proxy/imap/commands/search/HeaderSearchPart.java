package rice.email.proxy.imap.commands.search;

import java.util.*;

import rice.email.proxy.imap.ImapConnection;
import rice.email.proxy.mail.StoredMessage;
import rice.email.proxy.mail.*;
import rice.email.proxy.mailbox.*;

public class HeaderSearchPart extends SearchPart {

  String header;
  String string;
  
  public HeaderSearchPart(String header, String string) {
    this.header = header;
    this.string = string;
  }

  public boolean includes(StoredMessage msg) {
    try {
      Enumeration e = msg.getMessage().getMatchingHeaderLines(new String[] {header});

      while (e.hasMoreElements()) {
        if (((String) e.nextElement()).toLowerCase().indexOf(string.toLowerCase()) >= 0) {
          return true;
        }
      }

      return false;
    } catch (MailboxException e) {
      System.out.println("Exception " + e + " was thrown in HeaderSearchPart.");
      return false;
    } catch (MailException e) {
      System.out.println("Exception " + e + " was thrown in HeaderSearchPart.");
      return false;
    }
  }
}