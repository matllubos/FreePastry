package rice.email.proxy.imap.commands.fetch;

import rice.*;
import rice.Continuation.*;

import rice.email.*;
import rice.email.proxy.mail.*;
import rice.email.proxy.imap.commands.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.util.*;

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
        return "" + message.getSize();
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
        msg.getFlagList().addFlag("\\SEEN");
        msg.getFlagList().commit();
        result += " ";

        FetchPart handler = FetchCommand.regestry.getHandler("FLAGS");
        handler.setConn(getConn());
        result += handler.fetch(msg, "FLAGS");
      }

      return result;
    } else {
      return "NIL";
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

        if (i-1 < parts.length) {
          return fetchPart(breq, types, parts[i-1]);
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

      if (content instanceof EmailMessagePart) {
        types.add(0, type);
        return fetchPart(breq, types, (EmailMessagePart) content);
      } else if (type.equals("MIME") && (! (part instanceof EmailMessagePart))) {
        return fetchHeader(part);
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

    System.out.println("DIDN'T KNOW WHAT TO DO WITH " + part.getClass().getName() + " " + content.getClass().getName() + " " + type);

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
      
      InternetHeaders iHeaders = new InternetHeaders(new ByteArrayInputStream(data.getData()));
      Enumeration headers;

      if (exclude) {
        headers = iHeaders.getMatchingHeaderLines(parts);
      } else {
        headers = iHeaders.getNonMatchingHeaderLines(parts);
      }

      return format(collapse(headers) + "\r\n");
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

  public String fetchAll(EmailMultiPart part) throws MailboxException {
    String type = part.getType();
    String seperator = type.substring(type.toLowerCase().indexOf("boundary=\"")+10, type.length()-1);
    StringBuffer result = new StringBuffer();

    ExternalContinuation c = new ExternalContinuation();
    part.getContent(c);
    c.sleep();

    if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

    EmailContentPart[] parts = (EmailContentPart[]) c.getResult();

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

    return new String(((EmailData) c.getResult()).getData());
  }

  public String fetchAll(EmailHeadersPart part) throws MailboxException {
    ExternalContinuation c = new ExternalContinuation();
    part.getHeaders(c);
    c.sleep();

    if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

    EmailData headers = (EmailData) c.getResult();
    
    c = new ExternalContinuation();
    part.getContent(c);
    c.sleep();

    if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

    EmailContentPart data = (EmailContentPart) c.getResult();

    return new String(headers.getData()) + "\r\n" + fetchAll(data);
  }

  private String getRange(BodyPartRequest breq, String content) {
    if (breq.hasRange()) {
      content = content.substring(breq.getRangeStart(), breq.getRangeStart() + breq.getRangeLength());
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
