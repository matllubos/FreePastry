package rice.email.proxy.mailbox.postbox;

import rice.*;

import rice.post.*;

import rice.email.*;

import rice.email.proxy.mail.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.util.*;

import java.io.*;

import java.util.*;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.MessagingException;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.Session;

public class PostMessage implements StoredMessage {

  private StoredEmail email;

  private int sequence;

  private Folder folder;

  private MimeMessage message;

  public PostMessage(StoredEmail storedEmail, int sequence, Folder folder) {
    this.email = storedEmail;
    this.sequence = sequence;
    this.folder = folder;
  }

  public int getUID() {
    return email.getUID();
  }

  public int getSequenceNumber() {
    return sequence;
  }

  public Folder getFolder() {
    return folder;
  }

  public StoredEmail getStoredEmail() {
    return email;
  }

  public MimeMessage getMessage() throws MailboxException {
    if (message != null) {
      return message;
    } else {
      try {
        final Exception[] exception = new Exception[1];
        final Object[] result = new Object[3];
        final Object wait = "wait";

        Continuation c = new Continuation() {
          public void receiveResult(Object o) {
            if (result[0] == null) {
              result[0] = o;
            } else if (result[1] == null) {
              result[1] = o;
            } else {
              result[2] = o;
            }

            synchronized (wait) { wait.notify(); }
          }

          public void receiveException(Exception e) {
            exception[0] = e;
            synchronized (wait) { wait.notify(); }
          }
        };

        email.getEmail().getHeaders(c);

        synchronized (wait) { if ((result[0] == null) && (exception[0] == null)) wait.wait(); }

        if (exception[0] != null) {
          throw new MailboxException(exception[0]);
        }

        email.getEmail().getBody(c);

        synchronized (wait) { if ((result[1] == null) && (exception[0] == null)) wait.wait(); }

        if (exception[0] != null) {
          throw new MailboxException(exception[0]);
        }

        email.getEmail().getAttachments(c);

        synchronized (wait) { if ((result[2] == null) && (exception[0] == null)) wait.wait(); }

        if (exception[0] != null) {
          throw new MailboxException(exception[0]);
        }

        EmailData headers = (EmailData) result[0];
        EmailData body = (EmailData) result[1];
        EmailData[] attachments = (EmailData[]) result[2];

        javax.mail.internet.MimeMessage mimeMessage = new javax.mail.internet.MimeMessage((javax.mail.Session) null);

        if (attachments.length == 0) {
          mimeMessage.setText(new String(body.getData()));
        } else {
          MimeMultipart part = new MimeMultipart();

          mimeMessage.setContent(part);

          part.addBodyPart(new MimeBodyPart(new ByteArrayInputStream(body.getData())));

          for (int i=0; i<attachments.length; i++) {
            part.addBodyPart(new MimeBodyPart(new ByteArrayInputStream(attachments[i].getData())));
          }
        }

        StringTokenizer st = new StringTokenizer(new String(headers.getData()), "\n");

        while (st.hasMoreTokens()) {
          String token = st.nextToken();
          if (token.indexOf("Content-Type:") == -1) {
            mimeMessage.addHeaderLine(token);
          }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mimeMessage.writeTo(baos);

        message = new MimeMessage(new StringBufferResource(baos.toString()));

        return message;
      } catch (InterruptedException e) {
        throw new MailboxException(e);
      } catch (MailException e) {
        throw new MailboxException(e);
      } catch (MessagingException e) {
        throw new MailboxException(e);
      } catch (IOException e) {
        throw new MailboxException(e);
      }
    }
  }
  
  public FlagList getFlagList() {
    return PostFlagList.get(this);
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

  public static Email parseEmail(Resource content) throws MailboxException {
    try {
      Properties props = new Properties();
      Session session = Session.getDefaultInstance(props, null);
      javax.mail.internet.MimeMessage mm = new javax.mail.internet.MimeMessage(session, content.getInputStream());

      Address froms[] = mm.getFrom();
      Address to[] = mm.getRecipients(Message.RecipientType.TO);
      Address cc[] = mm.getRecipients(Message.RecipientType.CC);
      Address bcc[] = mm.getRecipients(Message.RecipientType.BCC);

      if (to == null) to = new Address[0];
      if (cc == null) cc = new Address[0];
      if (bcc == null) bcc = new Address[0];

      PostUserAddress from = new PostUserAddress("Unknown");

      if (froms.length > 0) {
        from = new PostUserAddress(((InternetAddress) froms[0]).getAddress());
      }

      PostEntityAddress[] recipients = new PostEntityAddress[to.length + cc.length + bcc.length];

      for (int i=0; i<recipients.length; i++) {
        if (i < to.length) {
          recipients[i] = new PostUserAddress(((InternetAddress) to[i]).getAddress());
        } else if (i < to.length + cc.length) {
          recipients[i] = new PostUserAddress(((InternetAddress) cc[i-to.length]).getAddress());
        } else {
          recipients[i] = new PostUserAddress(((InternetAddress) bcc[i-cc.length-to.length]).getAddress());
        }
      }

      ByteArrayOutputStream baos = new ByteArrayOutputStream();

      EmailData headers = null;
      EmailData body = null;
      EmailData[] attachments = null;

      String headersText = "";

      Enumeration e = mm.getAllHeaderLines();

      while (e.hasMoreElements()) {
        String header = (String) e.nextElement();
        
	if (header.charAt(header.length()-1) == '\n') {
          headersText += e.nextElement();
        } else {
          headersText += e.nextElement() + "\n";
        }
      }

      headers = new EmailData(headersText.getBytes());

      System.out.println("Found headers: \n" + headersText);

      if (mm.getContent() instanceof MimeMultipart) {
        MimeMultipart part = (MimeMultipart) mm.getContent();

        part.getBodyPart(0).writeTo(baos);
        body = new EmailData(baos.toByteArray());

        System.out.println("Found body: \n" + new String(baos.toByteArray()));
        baos.reset();

        attachments = new EmailData[part.getCount() - 1];

        for (int i=1; i<part.getCount(); i++) {
          part.getBodyPart(i).writeTo(baos);
          attachments[i-1] = new EmailData(baos.toByteArray());

          System.out.println("Found attachment: \n" + (new String(baos.toByteArray())));

          baos.reset();
        }
      } else {
        body = new EmailData(((String) mm.getContent()).getBytes());

        System.out.println("Found body: \n" + mm.getContent());

        attachments = new EmailData[0];
      }

      return new Email(from, recipients, headers, body, attachments);
    } catch (IOException e) {
      throw new MailboxException(e);
    } catch (MessagingException e) {
      throw new MailboxException(e);
    }
  }
}



