package rice.email.proxy.mailbox.postbox;

import rice.*;
import rice.Continuation.*;

import rice.post.*;

import rice.email.*;

import rice.email.proxy.mail.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.util.*;

import java.io.*;

import java.util.*;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.MessagingException;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.Session;

import javax.activation.*;

import java.awt.datatransfer.*;

public class PostMessage implements StoredMessage {

  public static String UNSECURE_SUBJECT_TITLE = "[UNSECURE]";
  
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

  public void setSequenceNumber(int num) {
    sequence = num;
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
        ExternalContinuation c = new ExternalContinuation();
        email.getEmail().getContent(c);
        c.sleep();

        if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

        EmailPart part = (EmailPart) c.getResult();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        getMessage(part).writeTo(baos);

        message = new MimeMessage(new StringBufferResource(baos.toString()));

        return message;
      } catch (MailException e) {
        throw new MailboxException(e);
      } catch (MessagingException e) {
        throw new MailboxException(e);
      } catch (IOException e) {
        throw new MailboxException(e);
      }
    }
  }

  private Object getContent(EmailContentPart emailPart) throws MessagingException, IOException, MailboxException {
    ExternalContinuation c = new ExternalContinuation();
    emailPart.getContent(c);
    c.sleep();

    if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

    if (emailPart instanceof EmailMultiPart) {
      MimeMultipart part = new MimeMultipart();

      EmailPart[] parts = (EmailPart[]) c.getResult();

      for (int i=0; i<parts.length; i++) {
        part.addBodyPart(getPart(parts[i]));
      }

      return part;
    } else if (emailPart instanceof EmailSinglePart) {
      return new String(((EmailData) c.getResult()).getData());
    } else {
      throw new MailboxException("EmailPart " + emailPart.getClass().getName() + " not recognized!");
    }
  }

  private MimeBodyPart getPart(EmailPart emailPart) throws MessagingException, IOException, MailboxException {
    MimeBodyPart part = new MimeBodyPart();

    ExternalContinuation c1 = new ExternalContinuation();
    emailPart.getHeaders(c1);
    c1.sleep();

    if (c1.exceptionThrown()) { throw new MailboxException(c1.getException()); }

    String headers = new String(((EmailData) c1.getResult()).getData());

    ExternalContinuation c2 = new ExternalContinuation();
    emailPart.getContent(c2);
    c2.sleep();

    if (c2.exceptionThrown()) { throw new MailboxException(c2.getException()); }

    if (c2.getResult() instanceof EmailMultiPart) {
      StringTokenizer st = new StringTokenizer(headers, "\n");

      while (st.hasMoreTokens()) {
        part.addHeaderLine(st.nextToken());
      }
      
      part.setContent((MimeMultipart) getContent((EmailContentPart) c2.getResult()));
    } else if (c2.getResult() instanceof EmailSinglePart) {
      String result = (String) getContent((EmailContentPart) c2.getResult());
      InternetHeaders iHeaders = new InternetHeaders(new ByteArrayInputStream(headers.getBytes()));
      byte[] content = result.getBytes();
      part = new MimeBodyPart(iHeaders, content);
    } else {
      throw new MailboxException("EmailPart " + c2.getResult().getClass().getName() + " not recognized!");
    }
      
    return part;
  }

  private javax.mail.internet.MimeMessage getMessage(EmailPart emailPart) throws MessagingException, IOException, MailboxException {
    Properties props = new Properties();
    Session session = Session.getDefaultInstance(props, null);
    javax.mail.internet.MimeMessage mm = new javax.mail.internet.MimeMessage(session);

    MimeBodyPart part = getPart(emailPart);
    Enumeration e = part.getAllHeaderLines();

    while (e.hasMoreElements()) {
      mm.addHeaderLine(e.nextElement().toString());
    }

    if (part.getContent() instanceof MimeMultipart) {
      mm.setContent((MimeMultipart) part.getContent());
    } else {
      mm.setContent(part.getContent(), part.getContentType());
    }
    
    return mm;
  }
  
  public FlagList getFlagList() {
    return PostFlagList.get(this);
  }

  public void purge() throws MailboxException {
    ExternalContinuation c = new ExternalContinuation();
    folder.removeMessage(email, c);
    c.sleep();

    if (c.exceptionThrown()) { throw new MailboxException(c.getException()); } 
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

      MailAddress[] recipients = new MailAddress[to.length + cc.length + bcc.length];

      for (int i=0; i<recipients.length; i++) {
        if (i < to.length) {
          recipients[i] = new MailAddress(((InternetAddress) to[i]).getAddress());
        } else if (i < to.length + cc.length) {
          recipients[i] = new MailAddress(((InternetAddress) cc[i-to.length]).getAddress());
        } else {
          recipients[i] = new MailAddress(((InternetAddress) bcc[i-cc.length-to.length]).getAddress());
        }
      }

      return parseEmail(recipients, content);
    } catch (IOException e) {
      throw new MailboxException(e);
    } catch (MessagingException e) {
      throw new MailboxException(e);
    } catch (MalformedAddressException e) {
      throw new MailboxException(e);
    }
  }

  public static Email parseEmail(MailAddress[] addresses, Resource content) throws MailboxException {
    return parseEmail(addresses, content, null);
  }
  
  public static Email parseEmail(MailAddress[] addresses, Resource content, PostEntityAddress address) throws MailboxException {
    try {
      Properties props = new Properties();
      Session session = Session.getDefaultInstance(props, null);
      javax.mail.internet.MimeMessage mm = new javax.mail.internet.MimeMessage(session, content.getInputStream());

      Address froms[] = mm.getFrom();
      PostUserAddress from = new PostUserAddress("Unknown");

      if (froms.length > 0) {
        from = new PostUserAddress(((InternetAddress) froms[0]).getAddress());
      }

      if (address != null) {
        from = (PostUserAddress) address;
      }

      PostEntityAddress[] recipients = new PostEntityAddress[addresses.length];

      for (int i=0; i<recipients.length; i++) {
        recipients[i] = new PostUserAddress(addresses[i].toString());
      }

      if (address != null) {
        mm.setSubject(UNSECURE_SUBJECT_TITLE + " " + mm.getSubject());
      }

      Enumeration e = mm.getAllHeaderLines();

      String headersText = "";
      
      while (e.hasMoreElements()) {
        String header = (String) e.nextElement();
        headersText += header.replaceAll("\n", "") + "\n";
      }

      EmailData headers = new EmailData(headersText.getBytes());
      EmailContentPart part = null;

      if (mm.getContent() instanceof MimeMultipart) {
        part = getContent(mm.getContent());
      } else {
        part = getContent(mm.getRawInputStream());
      }
      
      return new Email(from, recipients, new EmailPart(headers, part));
    } catch (IOException e) {
      throw new MailboxException(e);
    } catch (MessagingException e) {
      throw new MailboxException(e);
    }
  }

  private static EmailContentPart getContent(Object o) throws MessagingException, IOException {
    if (o instanceof MimeMultipart) {
      MimeMultipart part = (MimeMultipart) o;
      EmailPart[] parts = new EmailPart[part.getCount()];

      for (int i=0; i<parts.length; i++) {
        parts[i] = getPart((MimeBodyPart) part.getBodyPart(i));
      }

      return new EmailMultiPart(parts);
    } if (o instanceof InputStream) {
      String data = StreamUtils.toString(new InputStreamReader((InputStream) o));
      return new EmailSinglePart(new EmailData(data.getBytes()));
    } else {
      throw new MessagingException("EmailPart " + o.getClass().getName() + " not recognized!");
    }
  }

  private static EmailPart getPart(MimeBodyPart part) throws MessagingException, IOException {
    String headersText = "";
    Enumeration e = part.getAllHeaderLines();

    while (e.hasMoreElements()) {
      String header = (String) e.nextElement();
      headersText += header.replaceAll("\n", "") + "\n";
    }

    EmailData headers = new EmailData(headersText.getBytes());
    EmailContentPart content = null;
    
    if (part.getContent() instanceof MimeMultipart) {
      content = getContent(part.getContent());
    } else {
      content = getContent(part.getRawInputStream());
    }

    return new EmailPart(headers, content);
  }
}



