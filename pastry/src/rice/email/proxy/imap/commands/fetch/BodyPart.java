package rice.email.proxy.imap.commands.fetch;

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
    try {
      if (part instanceof RFC822PartRequest) {
        RFC822PartRequest rreq = (RFC822PartRequest) part;

        if (rreq.getType().equals("SIZE")) {
          return "" + msg.getMessage().getSize();
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
          InputStream stream = msg.getMessage().getMessage().getRawInputStream();
          StringWriter writer = new StringWriter();

          StreamUtils.copy(new InputStreamReader(stream), writer);
          Enumeration headers = msg.getMessage().getMessage().getAllHeaderLines();
          String header = "";
          
          while (headers.hasMoreElements()) {
            header += headers.nextElement() + "\r\n";
          }

          header += "\r\n" + writer.toString();
          
          result += "{" + header.length() + "}\r\n" + header;
        } else {
          result += fetchPart(breq, clone(breq.getType()), msg.getMessage().getMessage(), true);
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
    } catch (IOException ioe) {
      throw new MailboxException(ioe);
    } catch (MailException me) {
      throw new MailboxException(me);
    } catch (MessagingException me) {
      throw new MailboxException(me);
    }
  }

  protected String fetchPart(BodyPartRequest breq, List types, MimePart content, boolean topLevel) throws MailboxException {
    if (types.size() == 0) {
      return fetchAll(breq, content);
    }

    String type = (String) types.remove(0);
    Object part = null;

    try {
      part = content.getContent();
    } catch (IOException ioe) {
      throw new MailboxException(ioe);
    } catch (MessagingException me) {
      throw new MailboxException(me);
    }

    try {
      int i = Integer.parseInt(type);

      if ((part instanceof String) && (i == 1) && (types.size() == 0) && topLevel) {
        return fetchAll(breq, content);
      } else if (part instanceof MimeMultipart) {
        MimeMultipart mime = (MimeMultipart) part;

        if (i-1 < mime.getCount()) {
          return fetchPart(breq, types, (MimeBodyPart) mime.getBodyPart(i-1), false);
        }
      }
    } catch (NumberFormatException e) {
      if (topLevel || (part instanceof MimePart)) {
        if (type.equals("HEADER")) {
          return fetchHeader((MimePart) content);
        } else if (type.equals("HEADER.FIELDS")) {
          return fetchHeader((MimePart) content, split(breq.getPartIterator()));
        } else if (type.equals("HEADER.FIELDS.NOT")) {
          return fetchHeader((MimePart) content, split(breq.getPartIterator()), false);
        } else if (type.equals("TEXT")) {
          return fetchAll(breq, content);
        } else {
          throw new MailboxException("Unknown section text specifier");
        }
      } else if ((type.equals("MIME")) && (!topLevel)) {
        return fetchHeader((MimePart) content);
      }
    } catch (MessagingException me) {
      throw new MailboxException(me);
    }

    return "\"\"";
  }

  protected String fetchHeader(MimePart msg) throws MailboxException {
    return fetchHeader(msg, new String[0], false);
  }

  protected String fetchHeader(MimePart msg, String[] parts) throws MailboxException {
    return fetchHeader(msg, parts, true);
  }

  protected String fetchHeader(MimePart msg, String[] parts, boolean exclude) throws MailboxException {
    try {
      Enumeration headers;

      if (exclude) {
        headers = msg.getMatchingHeaderLines(parts);
      } else {
        headers = msg.getNonMatchingHeaderLines(parts);
      }

      String result = collapse(headers) + "\r\n";
      
      return "{" + result.length() + "}\r\n" + result;
    } catch (MessagingException me) {
      throw new MailboxException(me);
    }
  }

  public String fetchAll(BodyPartRequest breq, Object data) throws MailboxException {
    try {
      if (data instanceof String) {
        String content = getRange(breq, "" + data);

        if (content.equals("")) {
          return "\"\"";
        } else {
          return "{" + content.length() + "}\r\n" + content;
        }
      } else if (data instanceof MimeBodyPart) {
        MimeBodyPart mime = (MimeBodyPart) data;

        InputStream stream = mime.getRawInputStream();
        StringWriter writer = new StringWriter();

        StreamUtils.copy(new InputStreamReader(stream), writer);

        String content = getRange(breq, writer.toString());
        return "{" + content.length() + "}\r\n" + content;
      } else if (data instanceof javax.mail.internet.MimeMessage) {
        javax.mail.internet.MimeMessage mime = (javax.mail.internet.MimeMessage) data;

        InputStream stream = mime.getRawInputStream();
        StringWriter writer = new StringWriter();

        StreamUtils.copy(new InputStreamReader(stream), writer);

        String content = getRange(breq, writer.toString());
        return "{" + content.length() + "}\r\n" + content;
      } else if (data instanceof MimeMultipart) {
        MimeMultipart mime = (MimeMultipart) data;

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        mime.writeTo(stream);

        String content = getRange(breq, stream.toString());
        return "{" + content.length() + "}\r\n" + content;
      } else {
        return "NIL";
      }
    } catch (IOException ioe) {
      throw new MailboxException(ioe);
    } catch (MessagingException me) {
      throw new MailboxException(me);
    }
  }

  private String getRange(BodyPartRequest breq, String content) {
    if (breq.hasRange()) {
      content = content.substring(breq.getRangeStart(), breq.getRangeStart() + breq.getRangeLength());
    }

    return content;
  }
}
