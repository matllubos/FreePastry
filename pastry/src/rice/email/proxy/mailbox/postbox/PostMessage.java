package rice.email.proxy.mailbox.postbox;

import rice.*;
import rice.Continuation.*;

import rice.p2p.commonapi.*;

import rice.post.*;

import rice.email.*;

import rice.email.proxy.mail.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.util.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;

import java.io.*;
import java.net.*;

import java.util.*;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.AddressException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimePart;
import javax.mail.internet.ParseException;
import javax.mail.Multipart;
import javax.mail.MessagingException;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.Session;

import javax.activation.*;

import java.awt.datatransfer.*;

public class PostMessage implements StoredMessage {

  public static String UNSECURE_HEADER_LINE = "X-ePOST-Secure: False";
  public static String SECURE_HEADER_LINE = "X-ePOST-Secure: True";
  public static String IMAGE_URL_HEADER_LINE = "X-Image-Url: http://www.epostmail.org/images/epost-badge.png";
  
  private StoredEmail email;

  private int sequence;

  private Folder folder;
  
  public static IdFactory factory;

  public PostMessage(StoredEmail storedEmail, int sequence, Folder folder) {
    this.email = storedEmail;
    this.sequence = sequence;
    this.folder = folder;
  }

  public int getUID() {
    return email.getUID();
  }
  
  public long getInternalDate() {
    return email.getInternalDate();
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

  public Email getMessage() throws MailboxException {
    return email.getEmail();
  }
  
  public FlagList getFlagList() {
    return PostFlagList.get(this);
  }

  public void purge() throws MailboxException {
    try {
    ExternalContinuationRunnable c = new ExternalContinuationRunnable() {
      protected void execute(Continuation c) {
        folder.removeMessage(email, c);
      }
    };
    c.invoke(folder.getPost().getEnvironment());
    } catch (Exception e) {
      throw new MailboxException(e);
    }
  }
  
  protected static PostUserAddress[] getAddresses(MimeParser parser, String field, Environment env) throws MailboxException {
    try {
      String value = parser.getHeaderValue(field);
      Vector result = new Vector();
      
      if (value == null)
        value = "";
      
      while (value.length() > 0) {
        if ((value.indexOf("<") < 0) || (value.indexOf("<") == value.length() - 1)) break;
        value = value.substring(0, value.indexOf("<") + 1);
        
        if (value.indexOf(">") < 0) break;
        result.add(new PostUserAddress(factory, value.substring(0, value.indexOf(">")), env));
        
        value = value.substring(value.indexOf(">"));
      }
      
      return (PostUserAddress[]) result.toArray(new PostUserAddress[0]);
    } catch (MimeException e) {
      throw new MailboxException(e);
    }
  }

  public static Email parseEmail(InetAddress local, InetAddress remote, Resource content, Environment env) throws MailboxException {
    try {
      MimeParser parser = new MimeParser(content.getInputStream());
      if (parser.next() != MimeParser.START_HEADERS_PART)
        throw new MailboxException("ERROR: Parsing Mime message initially returned " + parser.getEventType());
      
      PostUserAddress[] froms = getAddresses(parser, "from", env);
      PostUserAddress[] to = getAddresses(parser, "to", env);
      PostUserAddress[] cc = getAddresses(parser, "cc", env);
      PostUserAddress[] bcc = getAddresses(parser, "bcc", env);

      PostUserAddress[] recipients = new PostUserAddress[to.length + cc.length + bcc.length];
 
      System.arraycopy(to, 0, recipients, 0, to.length);
      System.arraycopy(cc, 0, recipients, to.length, cc.length);
      System.arraycopy(bcc, 0, recipients, to.length + cc.length, bcc.length);
      
      return parseEmail(local, remote, recipients, content, env);
    } catch (IOException e) {
      throw new MailboxException(e);
    }
  }

  public static Email parseEmail(InetAddress local, InetAddress remote, PostEntityAddress[] addresses, Resource content, Environment env) throws MailboxException {
    return parseEmail(local, remote, addresses, content, null, env);
  }
  
  public static Email parseEmail(InetAddress local, InetAddress remote, PostEntityAddress[] recipients, Resource content, PostEntityAddress address, Environment env) throws MailboxException {    
    try {
      MimeParser parser = new MimeParser(content.getInputStream());
      if (parser.next() != MimeParser.START_HEADERS_PART)
        throw new MailboxException("ERROR: Parsing Mime message initially returned " + parser.getEventType());      
      
      PostUserAddress[] froms = getAddresses(parser, "from", env);
      PostUserAddress from = new PostUserAddress(factory, "Unknown", env);
      
      if ((froms != null) && (froms.length > 0)) 
        from = froms[0];

      String extraHeaders = "";
      
      if ((address != null) && (address.equals(from))) 
        extraHeaders += SECURE_HEADER_LINE + "\r\n";
      else
        extraHeaders += UNSECURE_HEADER_LINE + "\r\n";
      
      if (remote != null)
        extraHeaders += "Received: from " + remote.getHostAddress() + " by " + local.getHostAddress() + " via SMTP; " + MimeMessage.dateReader.format(new Date(env.getTimeSource().currentTimeMillis())) + "\r\n";

      if (address != null) 
        from = (PostUserAddress) address;
      
      return new Email(from, recipients, processMessage(parser, extraHeaders, env));
    } catch (IOException ioe) {
      throw new MailboxException(ioe);
    }
  }
  
  private static EmailMessagePart processMessage(MimeParser parser, String extraHeaders, Environment env) throws MailboxException {
    try {
      byte[] headers = parser.getHeader();
      
      if ((extraHeaders != null) && (extraHeaders.length() > 0)) {
        byte[] extra = extraHeaders.getBytes();
        byte[] tmp = new byte[headers.length+extra.length];
        
        System.arraycopy(headers, 0, tmp, 0, headers.length);
        System.arraycopy(extra, 0, tmp, headers.length, extra.length);
        headers = tmp;
      }
      
      return new EmailMessagePart(new EmailData(headers), process(parser, env));
    } catch (MimeException e) {
      throw new MailboxException(e);
    }
  }
  
  private static EmailContentPart process(MimeParser parser, Environment env) throws MailboxException {
    try {
      switch (parser.next()) {
        case MimeParser.START_HEADERS_PART:
          return processMessage(parser, null, env);
        case MimeParser.START_MULTIPART:
          return processMultipart(parser, env);
        case MimeParser.SINGLE_PART:
          return processSinglePart(parser);
        default:
          throw new MailboxException("Unexpected next value int processContent " + parser.getEventType());
      } 
    } catch (MimeException e) {
      throw new MailboxException(e);
    }
  }
  
  private static EmailMultiPart processMultipart(MimeParser parser, Environment env) throws MailboxException {
    try {
      Vector parts = new Vector();
      String boundary = "boundary=" + parser.getBoundary();

      Logger logger = env.getLogManager().getLogger(PostMessage.class, null);
      if (logger.level <= Logger.INFO) logger.log(
          "PROCESSING MIME MESSAGE WITH BOUNDARY " + boundary);
      
      while (true) {
        switch (parser.next()) {
          case MimeParser.START_HEADERS_PART:
            parts.add(new EmailHeadersPart(new EmailData(parser.getHeader()), process(parser, env)));
            break;
          case MimeParser.END_MULTIPART:
            return new EmailMultiPart((EmailHeadersPart[]) parts.toArray(new EmailHeadersPart[0]), boundary);
          default:
            throw new MailboxException("Unexpected next value int processMultipart " + parser.getEventType());
        }
      }  
    } catch (MimeException e) {
      throw new MailboxException(e);
    }
  }
  
  private static EmailSinglePart processSinglePart(MimeParser parser) throws MailboxException {
    try {
      return new EmailSinglePart(new EmailData(parser.getPart()));
    } catch (MimeException e) {
      throw new MailboxException(e);
    }
  }
  
  private static Object getContent(final EmailContentPart part, Environment env) throws MailboxException {
    try {
      ExternalContinuationRunnable c = new ExternalContinuationRunnable() {
        protected void execute(Continuation c) {
          part.getContent(c);
        }
      };
      return c.invoke(env); 
    } catch (Exception e) {
      throw new MailboxException(e);
    }
  }

  private static String getHeaders(final EmailMessagePart part, Environment env) throws MailboxException {
    try {
      ExternalContinuationRunnable c = new ExternalContinuationRunnable() {
        protected void execute(Continuation c) {
          part.getHeaders(c);
        }
      };
    
      return new String(((EmailData) c.invoke(env)).getData());
    } catch (Exception e) {
      throw new MailboxException(e);
    }
  }

  private static void setHeaders(String header, MimePart mm) throws MessagingException {
    String[] headers = header.split("\n");

    for (int i=0; i<headers.length; i++) {
      mm.addHeaderLine(headers[i]);
    }
  }
  
  private static String getContentType(String header, MimePart mm) throws MessagingException {
    String[] headers = header.split("\n");
    
    for (int i=0; i<headers.length; i++) {
      if (headers[i].indexOf("Content-Type:") > -1) {
        if (headers[i].indexOf(";") > -1) {
          return headers[i].substring(headers[i].indexOf(" ")+1, headers[i].indexOf(";"));
        } else {
          return headers[i].substring(headers[i].indexOf(" "));
        }
      }
    }
    
    return "text/plain";
  }

  public static javax.mail.internet.MimeMessage processEmailMessagePartSpecial(EmailMessagePart part, Environment env) throws MailboxException, MessagingException {
    Properties props = new Properties();
    Session session = Session.getDefaultInstance(props, null);
    javax.mail.internet.MimeMessage mm = new javax.mail.internet.MimeMessage(session);
    
    String contentType = getContentType(getHeaders(part, env), mm);
    setHeaders(getHeaders(part, env), mm);
    
    processEmailContentPart(mm, (EmailContentPart) getContent(part, env), contentType, env);
      
    return mm;
  }

  private static void processEmailContentPart(MimePart message, EmailContentPart part, String contentType, Environment env)
    throws MailboxException, MessagingException {
    Object result = null;
    
    if (part instanceof EmailSinglePart) {
      result = processEmailSinglePart((EmailSinglePart) part, env);
    } else if (part instanceof EmailMultiPart) {
      result = processEmailMultiPart((EmailMultiPart) part, env);
    } else if (part instanceof EmailMessagePart) {
      result = processEmailMessagePart((EmailMessagePart) part, env);
    } else {
      throw new MailboxException("Found unknown EmailContentPart subtype " + part);
    }

    if (result instanceof MimeMultipart) {
      message.setContent((MimeMultipart) result);
    } else {
      message.setContent(result, contentType);
    }
  }

  private static MimeBodyPart processEmailMessagePart(EmailMessagePart part, Environment env) throws MailboxException, MessagingException {
    MimeBodyPart body = new MimeBodyPart();
    String contentType = getContentType(getHeaders(part, env), body);
    EmailContentPart content = (EmailContentPart) getContent(part, env);
    setHeaders(getHeaders(part, env), body);
    
    // special thingy for embedded message
    if (content instanceof EmailMessagePart) {
      body.setContent(processEmailMessagePartSpecial((EmailMessagePart) content, env), contentType);
    } else {
      processEmailContentPart(body, content, contentType, env);
    }

    return body;
  }

  private static MimeMultipart processEmailMultiPart(EmailMultiPart part, Environment env) throws MailboxException, MessagingException {
    MimeMultipart multi = new MimeMultipart();
    EmailMessagePart[] parts = (EmailMessagePart[]) getContent(part, env);

    for (int i=0; i<parts.length; i++) {
      EmailMessagePart thisPart = parts[i];
      multi.addBodyPart(processEmailMessagePart(thisPart, env));
    }

    return multi;
  }

  private static String processEmailSinglePart(EmailSinglePart part, Environment env) throws MailboxException, MessagingException {
    return new String(((EmailData) getContent(part, env)).getData());
  }
      
}



