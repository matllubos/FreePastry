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
import javax.mail.internet.MimePart;
import javax.mail.Multipart;
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
        
        return new MimeMessage(processEmailMessagePartSpecial((EmailMessagePart) c.getResult()));
      } catch (MailException e) {
        throw new MailboxException(e);
      } catch (MessagingException e) {
        throw new MailboxException(e);
      }
    }
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

      if ((froms != null) && (froms.length > 0)) {
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
      
      return new Email(from, recipients, process(mm));
    } catch (IOException e) {
      throw new MailboxException(e);
    } catch (MessagingException e) {
      throw new MailboxException(e);
    }
  }

  private static EmailContentPart processContent(MimePart part) throws IOException, MessagingException {
    Object content = part.getContent();
    
    if (content instanceof Multipart) {
      return process((Multipart) content);
    } else if (content instanceof MimePart) {
      return process((MimePart) content);
    } else {
      if (part instanceof MimeBodyPart) {
        return process(part, ((MimeBodyPart) part).getRawInputStream());
      } else {
        return process(part, ((javax.mail.internet.MimeMessage) part).getRawInputStream());
      }
    }
  }

  private static EmailMessagePart process(MimePart mime) throws IOException, MessagingException {
    EmailData headers = new EmailData(getHeaders(mime).getBytes());
    EmailContentPart part = processContent(mime);

    return new EmailMessagePart(headers, part);
  }

  private static EmailMultiPart process(Multipart part) throws IOException, MessagingException {
    EmailMessagePart[] parts = new EmailMessagePart[part.getCount()];

    for (int i=0; i<parts.length; i++) {
      parts[i] = process((MimePart) part.getBodyPart(i));
    }

    return new EmailMultiPart(parts);
  }

  private static EmailSinglePart process(MimePart mime, InputStream stream) throws IOException, MessagingException {
    EmailData headers = new EmailData(getHeaders(mime).getBytes());
    
    String data = StreamUtils.toString(new InputStreamReader(stream));
    return new EmailSinglePart(headers, new EmailData(data.getBytes()));
  }


  

  private static String getHeaders(MimePart mime) throws MessagingException {
    Enumeration e = mime.getAllHeaderLines();

    String headersText = "";

    while (e.hasMoreElements()) {
      String header = (String) e.nextElement();
      headersText += header + "\n";
    }

    return headersText;
  }

  private static Object getContent(EmailContentPart part) throws MailboxException {
    ExternalContinuation c = new ExternalContinuation();
    part.getContent(c);
    c.sleep();

    if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

    return c.getResult();
  }

  private static String getHeaders(EmailSinglePart part) throws MailboxException {
    ExternalContinuation c = new ExternalContinuation();
    part.getHeaders(c);
    c.sleep();

    if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

    return new String(((EmailData) c.getResult()).getData());
  }

  private static String getHeaders(EmailMessagePart part) throws MailboxException {
    ExternalContinuation c = new ExternalContinuation();
    part.getHeaders(c);
    c.sleep();

    if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

    return new String(((EmailData) c.getResult()).getData());
  }

  private static String setHeaders(String header, MimePart mm) throws MessagingException {
    String[] headers = header.split("\n");
    String contentType = "text/plain";

    for (int i=0; i<headers.length; i++) {
      mm.addHeaderLine(headers[i]);
      if (headers[i].indexOf("Content-Type:") > -1) {
        if (headers[i].indexOf(";") > -1) {
          contentType = headers[i].substring(headers[i].indexOf(" ")+1, headers[i].indexOf(";"));
        } else {
          contentType = headers[i];
        }
      } 
    }

    return contentType;
  }

  public static javax.mail.internet.MimeMessage processEmailMessagePartSpecial(EmailMessagePart part) throws MailboxException, MessagingException {
    Properties props = new Properties();
    Session session = Session.getDefaultInstance(props, null);
    javax.mail.internet.MimeMessage mm = new javax.mail.internet.MimeMessage(session);
    
    String contentType = setHeaders(getHeaders(part), mm);
    EmailContentPart content = (EmailContentPart) getContent(part);
    processEmailContentPart(mm, content, contentType);

    return mm;
  }

  private static void processEmailContentPart(MimePart message, EmailContentPart part, String contentType)
    throws MailboxException, MessagingException {
    Object result = null;
    
    if (part instanceof EmailSinglePart) {
      result = processEmailSinglePart((EmailSinglePart) part);
    } else if (part instanceof EmailMultiPart) {
      result = processEmailMultiPart((EmailMultiPart) part);
    } else if (part instanceof EmailMessagePart) {
      result = processEmailMessagePart((EmailMessagePart) part);
    } else {
      throw new MailboxException("Found unknown EmailContentPart subtype " + part);
    }

    if (result instanceof MimeMultipart) {
      message.setContent((MimeMultipart) result);
    } else {
      message.setContent(result, contentType);
    }
  }

  private static MimeBodyPart processEmailMessagePart(EmailMessagePart part) throws MailboxException, MessagingException {
    MimeBodyPart body = new MimeBodyPart();
    String contentType = setHeaders(getHeaders(part), body);

    EmailContentPart content = (EmailContentPart) getContent(part);

    // special thingy for embedded message
    if (content instanceof EmailMessagePart) {
      body.setContent(processEmailMessagePartSpecial((EmailMessagePart) content), contentType);
    } else {
      processEmailContentPart(body, content, contentType);
    }

    return body;
  }

  private static MimeMultipart processEmailMultiPart(EmailMultiPart part) throws MailboxException, MessagingException {
    MimeMultipart multi = new MimeMultipart();
    EmailMessagePart[] parts = (EmailMessagePart[]) getContent(part);

    for (int i=0; i<parts.length; i++) {
      EmailMessagePart thisPart = parts[i];
      multi.addBodyPart(processEmailMessagePart(thisPart));
    }

    return multi;
  }

  private static String processEmailSinglePart(EmailSinglePart part) throws MailboxException, MessagingException {
    return new String(((EmailData) getContent(part)).getData());
  }
      
}



