package rice.email.proxy.mailbox.postbox;

import rice.*;
import rice.Continuation.*;

import rice.p2p.commonapi.*;

import rice.post.*;

import rice.email.*;

import rice.email.proxy.mail.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.util.*;

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
import java.util.Random;

public class PostMessage implements StoredMessage {

  public static String UNSECURE_HEADER_LINE = "X-EPost-Unsecure: True";
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
    ExternalContinuation c = new ExternalContinuation();
    folder.removeMessage(email, c);
    c.sleep();

    if (c.exceptionThrown()) { throw new MailboxException(c.getException()); } 
  }

  public static Email parseEmail(InetAddress remote, Resource content) throws MailboxException {
    try {
      Properties props = new Properties();
      Session session = Session.getDefaultInstance(props, null);
      javax.mail.internet.MimeMessage mm = new javax.mail.internet.MimeMessage(session, content.getInputStream());
            
      Address froms[] = null;
      Address to[] = null;
      Address cc[] = null;
      Address bcc[] = null;
      
      InternetAddress[] fallback = new InternetAddress[1];
      fallback[0] = new InternetAddress("malformed@cs.rice.edu","MalformedAddress");
      /* Ugly fix for malformed addreses ABP4 */
      try{
        froms = mm.getFrom();
      } catch (AddressException ae) { froms = fallback;}
      
      try{
        to = mm.getRecipients(Message.RecipientType.TO);
      } catch (AddressException ae) { to = fallback;}
      
      try{
        cc = mm.getRecipients(Message.RecipientType.CC);
      } catch (AddressException ae) { cc = fallback;}
      
      try{
        bcc = mm.getRecipients(Message.RecipientType.BCC);
      } catch (AddressException ae) { bcc = fallback;}
      
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

      return parseEmail(remote, recipients, content);
    } catch (IOException e) {
      throw new MailboxException(e);
    } catch(AddressException ae){
      System.out.println("***********************");
      ae.printStackTrace();
      System.out.println(ae.getRef());
      System.out.println(ae.getPos());
      System.out.println("***********************");
      throw new MailboxException(ae);
    } catch (MessagingException e) {
      throw new MailboxException(e);
    } catch (MalformedAddressException e) {
      throw new MailboxException(e);
    }
  }

  public static Email parseEmail(InetAddress remote, MailAddress[] addresses, Resource content) throws MailboxException {
    return parseEmail(remote, addresses, content, null);
  }
  
  public static Email parseEmail(InetAddress remote, MailAddress[] addresses, Resource content, PostEntityAddress address) throws MailboxException {
    PostEntityAddress[] recipients = new PostEntityAddress[addresses.length];
    
    for (int i=0; i<recipients.length; i++) 
      recipients[i] = new PostUserAddress(factory, addresses[i].toString());
    
    try {
      Properties props = new Properties();
      Session session = Session.getDefaultInstance(props, null);
      javax.mail.internet.MimeMessage mm = new javax.mail.internet.MimeMessage(session, content.getInputStream());
      
      Address froms[] = mm.getFrom();
      PostUserAddress from = new PostUserAddress(factory, "Unknown");

      if ((froms != null) && (froms.length > 0)) 
        from = new PostUserAddress(factory, ((InternetAddress) froms[0]).getAddress());

      if ((address != null) && (! address.equals(from))) 
        mm.addHeaderLine(UNSECURE_HEADER_LINE);

      if (address != null) 
        from = (PostUserAddress) address;
      
      try {
        if ((mm.getHeader("X-Image-Url") == null) || (mm.getHeader("X-Image-Url").length == 0))
          mm.addHeaderLine(IMAGE_URL_HEADER_LINE);

        if (remote != null)
          mm.addHeaderLine("Received: from " + remote.getHostAddress() + " by " + InetAddress.getLocalHost().getHostAddress() + " via SMTP; " + MimeMessage.dateReader.format(new Date(System.currentTimeMillis())));
      } catch (Exception e) {
        System.out.println("ERROR: Got exception " + e + " adding breadcrumb...");
      }
      return new Email(from, recipients, (EmailMessagePart) process(mm));
    } catch (Exception e) {
      try {
        System.out.println("ERROR: Got Exception " + e + " while parsing message - defaulting to dumb parsing.");
        BufferedReader r = new BufferedReader(new InputStreamReader(content.getInputStream()));
        
        StringBuffer headers = new StringBuffer();
        StringBuffer body = new StringBuffer();
        String line = null;
        boolean done = false;
        
        while ((line = r.readLine()) != null) {
          if (done) {
            body.append(line + "\r\n");
          } else {
            if (line.trim().equals("")) {
              done = true;
            } else {
              headers.append(line + "\r\n");
            }
          }
        }
        
        EmailSinglePart esp = new EmailSinglePart(new EmailData(body.toString().getBytes()));
        EmailMessagePart emp = new EmailMessagePart(new EmailData(headers.toString().getBytes()), esp);
        return new Email((PostUserAddress) address, recipients, emp);
      } catch (IOException ioe) {
        throw new MailboxException(ioe);
      }
    }
  }
  
  private static void walker(EmailContentPart part, String indent) {
    if (part instanceof EmailMultiPart) {
      EmailMultiPart multi = (EmailMultiPart) part;
      System.out.println(indent + "EmailMultiPart");
      for (int i=0; i<multi.content.length; i++) 
        walker(multi.content[i], indent + "  ");
    } else if (part instanceof EmailSinglePart) {
      System.out.println(indent + "EmailSinglePart");
    } else if (part instanceof EmailMessagePart)  {
      System.out.println(indent + "EmailMessagePart");
      walker(((EmailHeadersPart) part).content, indent + "  ");
    } else if (part instanceof EmailHeadersPart) {
      System.out.println(indent + "EmailHeadersPart");
      walker(((EmailHeadersPart) part).content, indent + "  ");
    } 
  }


  private static EmailContentPart processContent(MimePart part) throws IOException, MessagingException {
    try {
      Object content = part.getContent();
    
      if (content instanceof Multipart) {
        return process((Multipart) content, part.getContentType());
      } else if (content instanceof MimePart) {
        return process((MimePart) content);
      } else {
        if (part instanceof MimeBodyPart)
          return process(((MimeBodyPart) part).getRawInputStream());
        else if (part instanceof javax.mail.internet.MimeMessage)
          return process(((javax.mail.internet.MimeMessage) part).getRawInputStream());
        else
          return process(part.getInputStream());
      }
    } catch (UnsupportedEncodingException uex) {
      if (part instanceof MimeBodyPart)
        return process(((MimeBodyPart) part).getRawInputStream());
      else if (part instanceof javax.mail.internet.MimeMessage)
        return process(((javax.mail.internet.MimeMessage) part).getRawInputStream());
      else
        return process(part.getInputStream());
    }
  }

  private static EmailHeadersPart process(MimePart mime) throws IOException, MessagingException {
    EmailData headers = new EmailData(getHeaders(mime).getBytes());
    EmailContentPart part = processContent(mime);

    if (mime instanceof javax.mail.internet.MimeMessage)
      return new EmailMessagePart(headers, part);
    else
      return new EmailHeadersPart(headers, part);
  }

  private static EmailMultiPart process(Multipart part, String type) throws IOException, MessagingException {
    EmailHeadersPart[] parts = new EmailHeadersPart[part.getCount()];

    for (int i=0; i<parts.length; i++) {
      parts[i] = process((MimePart) part.getBodyPart(i));
    }

    return new EmailMultiPart(parts, type);
  }

  private static EmailSinglePart process(InputStream stream) throws IOException, MessagingException {
    String data = StreamUtils.toString(new InputStreamReader(stream));
    return new EmailSinglePart(new EmailData(data.getBytes()));
  }

  private static String getHeaders(MimePart mime) throws MessagingException {
    Enumeration e = mime.getAllHeaderLines();

    String headersText = "";

    while (e.hasMoreElements()) {
      String header = (String) e.nextElement();
      headersText += header + "\r\n";
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

  private static String getHeaders(EmailMessagePart part) throws MailboxException {
    ExternalContinuation c = new ExternalContinuation();
    part.getHeaders(c);
    c.sleep();

    if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }
    
    return new String(((EmailData) c.getResult()).getData());
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

  public static javax.mail.internet.MimeMessage processEmailMessagePartSpecial(EmailMessagePart part) throws MailboxException, MessagingException {
    Properties props = new Properties();
    Session session = Session.getDefaultInstance(props, null);
    javax.mail.internet.MimeMessage mm = new javax.mail.internet.MimeMessage(session);
    
    String contentType = getContentType(getHeaders(part), mm);
    setHeaders(getHeaders(part), mm);
    
    processEmailContentPart(mm, (EmailContentPart) getContent(part), contentType);
      
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
    String contentType = getContentType(getHeaders(part), body);
    EmailContentPart content = (EmailContentPart) getContent(part);
    setHeaders(getHeaders(part), body);
    
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



