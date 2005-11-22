package rice.email.proxy.imap.commands.fetch;

import rice.*;
import rice.Continuation.*;

import rice.email.*;
import rice.email.proxy.mail.*;
import rice.email.proxy.imap.commands.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.util.*;
import rice.environment.logging.Logger;

import java.io.*;

import java.util.*;

import javax.mail.*;
import javax.mail.internet.*;


public class BodyPart extends FetchPart {

  public boolean canHandle(Object req) {
    return ((req instanceof BodyPartRequest) ||
            (req instanceof RFC822PartRequest));
  }

  private String toSentenceCase(String s) {
    return s.substring(0,1).toUpperCase() + s.substring(1, s.length());
  }

  private String[] split(Iterator i) {
    Vector v = new Vector();

    while (i.hasNext()) {
      v.add(i.next());
    }

    return (String[]) v.toArray(new String[0]);
  }

  private String collapse(Enumeration e) {
    String result = "";

    while (e.hasMoreElements()) {
      String header = e.nextElement().toString();
      result += header + "\r\n";
    }

    return result;
  }

  private List clone(List l) {
    List ret = new ArrayList();

    for(int i=0; i<l.size(); i++) {
      ret.add(l.get(i));
    }

    return ret;
  }

  public String fetch(StoredMessage msg, Object part) throws MailboxException {
    return part.toString() + " " + fetchHandler(msg, part);
  }

  protected String fetchHandler(StoredMessage msg, Object part) throws MailboxException {
    ExternalContinuation c = new ExternalContinuation();
    msg.getMessage().getContent(c);
    c.sleep();

    if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

    EmailMessagePart message = (EmailMessagePart) c.getResult();
    
    if (part instanceof RFC822PartRequest) {
      RFC822PartRequest rreq = (RFC822PartRequest) part;

      if (rreq.getType().equals("SIZE")) {
        return "" + fetchSize(message);
      } else {
        BodyPartRequest breq = new BodyPartRequest();
 
        if (rreq.getType().equals("HEADER")) {
          breq.appendType("HEADER");
          breq.setPeek(true);
        } else if (rreq.getType().equals("TEXT")) {
          breq.appendType("TEXT");
        }

        return fetchHandler(msg, breq);
      }
    } else if (part instanceof BodyPartRequest) {
      String result = "";

      BodyPartRequest breq = (BodyPartRequest) part;

      if (breq.getType().size() == 0) {
        result = fetchAll(breq, message);
      } else {
        result = fetchPart(breq, clone(breq.getType()), message);
      }

      if ((! breq.getPeek()) &&	(! msg.getFlagList().isSeen())) {
        msg.getFlagList().setSeen(true);
        msg.getFlagList().commit();
        result += " ";

        FetchPart handler = FetchCommand.registry.getHandler("FLAGS");
        handler.setConn(getConn());
        result += handler.fetch(msg, "FLAGS");
      }

      return result;
    } else {
      return "NIL";
    }
  }

  protected String fetchSize(EmailMessagePart message) throws MailboxException {
    return "" + (message.getSize() + internalFetchSize(message));
  }
     
  protected int internalFetchSize(EmailContentPart content) throws MailboxException {
    if (content instanceof EmailHeadersPart) {
      return 2 + internalFetchSize(((EmailHeadersPart) content).content);
    } else if (content instanceof EmailMultiPart) {
      EmailMultiPart multi = (EmailMultiPart) content;
      int result = ((getBoundary(multi.type).length() + 6) * (multi.content.length + 1));
        
      for (int i=0; i<multi.content.length; i++)
        result += internalFetchSize(multi.content[i]);
          
      return result;
    } else {
      return 0;
    }
  }

  protected String fetchPart(BodyPartRequest breq, List types, EmailHeadersPart part) throws MailboxException {
    ExternalContinuation c = new ExternalContinuation();
    part.getContent(c);
    c.sleep();

    if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }    

    EmailContentPart content = (EmailContentPart) c.getResult();

    if (types.size() == 0) {
      return fetchAll(breq, content);
    }
    
    String type = (String) types.remove(0);

    try {
      // see if the part request is a number
      int i = Integer.parseInt(type);

      if (content instanceof EmailMultiPart) {
        c = new ExternalContinuation();
        ((EmailMultiPart) content).getContent(c);
        c.sleep();

        if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

        EmailHeadersPart[] parts = (EmailHeadersPart[]) c.getResult();

        if ((i > 0) && (i-1 < parts.length)) {
          return fetchPart(breq, types, parts[i-1]);
        } else {
          throw new MailboxException("Invalid body part specifier: " + i); 
        }
      } else if (content instanceof EmailMessagePart) {
        types.add(0, type);
        return fetchPart(breq, types, (EmailMessagePart) content);
      } else if ((i == 1) && (types.size() == 0)) {
        return fetchAll(breq, content);
      }
    } catch (NumberFormatException e) {
      if (content == null)
        throw new MailboxException("Could not properly parse content of " + part);

      if (type.equals("MIME") && (! (part instanceof EmailMessagePart))) {
        return fetchHeader(part);
      } else if (content instanceof EmailMessagePart) {
        types.add(0, type);
        return fetchPart(breq, types, (EmailMessagePart) content);
      } else {
        if (type.equals("HEADER")) {
          return fetchHeader(part);
        } else if (type.equals("HEADER.FIELDS")) {
          return fetchHeader(part, split(breq.getPartIterator()));
        } else if (type.equals("HEADER.FIELDS.NOT")) {
          return fetchHeader(part, split(breq.getPartIterator()), false);
        } else if (type.equals("TEXT")) {
          return fetchAll(breq, content);
        } else {
          throw new MailboxException("Unknown section text specifier");
        }
      }
    }

    Logger logger = _conn.getEnvironment().getLogManager().getLogger(BodyPart.class, null);
    if (logger.level <= Logger.WARNING) logger.log(
        "DIDN'T KNOW WHAT TO DO WITH " + part.getClass().getName() + " " + content.getClass().getName() + " " + type);

    return "\"\"";
  }

  protected String fetchHeader(EmailHeadersPart part) throws MailboxException {
    return fetchHeader(part, new String[0], false);
  }

  protected String fetchHeader(EmailHeadersPart part, String[] parts) throws MailboxException {
    return fetchHeader(part, parts, true);
  }

  protected String fetchHeader(EmailHeadersPart part, String[] parts, boolean exclude) throws MailboxException {
    try {
      ExternalContinuation c = new ExternalContinuation();
      part.getHeaders(c);
      c.sleep();

      if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

      EmailData data = (EmailData) c.getResult();
      
      if (data == null) {
        return format("Error: Unable to fetch data - did not exist in Past!\r\n");
      }
      
      InternetHeaders iHeaders = new InternetHeaders(new ByteArrayInputStream(data.getData()));
      Enumeration headers;

      if (exclude) {
        headers = iHeaders.getMatchingHeaderLines(parts);
      } else {
        headers = iHeaders.getNonMatchingHeaderLines(parts);
      }
      
      return format(collapse(headers).replaceAll("[\\u0080-\\uffff]", "?") + "\r\n");
    } catch (MessagingException e) {
      throw new MailboxException(e);
    }
  }

  public String fetchAll(BodyPartRequest breq, EmailContentPart part) throws MailboxException {
    return format(getRange(breq, fetchAll(part)));
  }

  public String fetchAll(EmailContentPart part) throws MailboxException {
    if (part instanceof EmailMultiPart)
      return fetchAll((EmailMultiPart) part);
    else if (part instanceof EmailSinglePart)
      return fetchAll((EmailSinglePart) part);
    else if (part instanceof EmailHeadersPart)
      return fetchAll((EmailHeadersPart) part);
    else
      throw new MailboxException("Unrecognized part " + part);
  }

  public String getBoundary(String type) {
    String seperator = type.substring(type.toLowerCase().indexOf("boundary=")+9, type.length());
    
    if (seperator.indexOf(";") >= 0)
      seperator = seperator.substring(0, seperator.indexOf(";"));
    
    return seperator.replaceAll("\"", "").replaceAll("'", "");
  }

  public String fetchAll(EmailMultiPart part) throws MailboxException {
    String seperator = getBoundary(part.getType());
    StringBuffer result = new StringBuffer();

    ExternalContinuation c = new ExternalContinuation();
    part.getContent(c);
    c.sleep();

    if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

    EmailContentPart[] parts = (EmailContentPart[]) c.getResult();
    
    if (parts == null) {
      return "Error: Unable to fetch data - did not exist in Past!\r\n";
    }

    for (int i=0; i<parts.length; i++) {
      result.append("--" + seperator + "\r\n" + fetchAll(parts[i]) + "\r\n");
    }

    result.append("--" + seperator + "--\r\n");

    return result.toString();
  }

  public String fetchAll(EmailSinglePart part) throws MailboxException {
    ExternalContinuation c = new ExternalContinuation();
    part.getContent(c);
    c.sleep();

    if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

    if (c.getResult() == null) {
      return "Unable to fetch data - did not exist in Past!\r\n";
    } else {
      return new String(((EmailData) c.getResult()).getData());
    }
  }

  public String fetchAll(EmailHeadersPart part) throws MailboxException {
    ExternalContinuation c = new ExternalContinuation();
    part.getHeaders(c);
    c.sleep();

    if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

    EmailData headers = (EmailData) c.getResult();
    
    if (headers == null) {
      headers = new EmailData("Error: Unable to fetch data - did not exist in Past!\r\n".getBytes());
    }
    
    c = new ExternalContinuation();
    part.getContent(c);
    c.sleep();

    if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

    EmailContentPart data = (EmailContentPart) c.getResult();

    return new String(headers.getData()).replaceAll("[\\u0080-\\uffff]", "?") + "\r\n" + fetchAll(data);
  }

  private String getRange(BodyPartRequest breq, String content) {
    if (breq.hasRange()) {
      if (breq.getRangeStart() > content.length()) 
        content = "";
      else
        content = content.substring(breq.getRangeStart());
      
      if (breq.getRangeLength() < content.length())
        content = content.substring(0, breq.getRangeLength());
    }

    return content;
  }

  private String format(String content) {
    if (content.equals("")) {
      return "\"\"";
    } else {
      return "{" + content.length() + "}\r\n" + content;
    }
  }
}
