package rice.email.proxy.mailbox.postbox;

import rice.*;

import rice.email.*;

import rice.email.proxy.mail.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.util.*;

import java.util.*;

public class PostMessage implements StoredMessage {

  private Email email;

  private int sequence;

  private Folder folder;

  public PostMessage(Email email, int sequence, Folder folder) {
    this.email = email;
    this.sequence = sequence;
    this.folder = folder;
  }

  public int getUID() {
    return sequence;
  }

  public int getSequenceNumber() {
    return sequence;
  }

  public MimeMessage getMessage() throws MailboxException {
    try {
      final Exception[] exception = new Exception[1];
      final Object[] result = new Object[1];
      final Object wait = "wait";

      Continuation c = new Continuation() {
        public void receiveResult(Object o) {
          result[0] = o;
          synchronized (wait) { wait.notify(); }
        }

        public void receiveException(Exception e) {
          exception[0] = e;
          synchronized (wait) { wait.notify(); }
        }
      };

      email.getBody(c);

      synchronized (wait) { if ((result[0] == null) && (exception[0] == null)) wait.wait(); }

      if (exception[0] != null) {
        throw new MailboxException(exception[0]);
      }

      String body = new String(((EmailData) result[0]).getData());

      return new MimeMessage(new StringBufferResource(body));
    } catch (InterruptedException e) {
      throw new MailboxException(e);
    } catch (MailException e) {
      throw new MailboxException(e);
    }
  }

  public FlagList getFlagList() {
    return PostFlagList.get(email);
  }

  public void purge() throws MailboxException {
    try {
      final Exception[] exception = new Exception[1];
      final Object[] result = new Object[1];
      final Object wait = "wait";

      Continuation c = new Continuation() {
        public void receiveResult(Object o) {
          result[0] = o;
          synchronized (wait) { wait.notify(); }
        }

        public void receiveException(Exception e) {
          exception[0] = e;
          synchronized (wait) { wait.notify(); }
        }
      };

      folder.removeMessage(email, c);

      synchronized (wait) { if ((result[0] == null) && (exception[0] == null)) wait.wait(); }

      if (exception[0] != null) {
        throw new MailboxException(exception[0]);
      }
    } catch (InterruptedException e) {
      throw new MailboxException(e);
    }  
  }
}