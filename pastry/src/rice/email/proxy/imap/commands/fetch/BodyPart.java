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

  public void fetch(StoredMessage msg, Object part) throws MailboxException {
    getConn().print(part.toString() + " ");

    fetchHandler(msg, part);
  }

  protected void fetchHandler(StoredMessage msg, Object part) throws MailboxException {
    try {
      if (part instanceof RFC822PartRequest) {
        RFC822PartRequest rreq = (RFC822PartRequest) part;

        if (rreq.getType().equals("SIZE")) {
          getConn().print("" + msg.getMessage().getSize());
        } else {
          BodyPartRequest breq = new BodyPartRequest();

          if (rreq.getType().equals("HEADER")) {
            breq.appendType("HEADER");
            breq.setPeek(true);
          } else if (rreq.getType().equals("TEXT")) {
            breq.appendType("TEXT");
          }

          fetchHandler(msg, breq);
        }
      } else if (part instanceof BodyPartRequest) {
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
          
          getConn().print("{" + header.length() + "}\r\n");
          getConn().print(header);
        } else {
          fetchPart(breq, clone(breq.getType()), msg.getMessage().getMessage(), true);
        }
      /*    
        if ((! breq.getPeek()) &&	(! msg.getFlagList().isSeen())) {
          msg.getFlagList().addFlag("\\SEEN");
          msg.getFlagList().commit();
          getConn().print(" ");

          FetchPart handler = FetchCommand.regestry.getHandler("FLAGS");
          handler.setConn(getConn());
          handler.fetch(msg, "FLAGS");
        } */
      } else {
        getConn().print("NIL");
      }
    } catch (IOException ioe) {
      throw new MailboxException(ioe);
    } catch (MailException me) {
      throw new MailboxException(me);
    } catch (MessagingException me) {
      throw new MailboxException(me);
    }
  }

  protected void fetchPart(BodyPartRequest breq, List types, MimeBodyPart content, boolean topLevel) throws MailboxException {
    try {
      if (types.size() == 0) {
        fetchAll(breq, content);
      } else {
        String type = (String) types.remove(0);

        if (type.equals("HEADER")) {
          if (topLevel || (content.getContent() instanceof MimePart)) 
            fetchHeader((MimePart) content);
          else
            getConn().print("\"\"");
        } else if (type.equals("HEADER.FIELDS")) {
          if (topLevel || (content.getContent() instanceof MimePart))
            fetchHeader((MimePart) content, split(breq.getPartIterator()));
          else
            getConn().print("\"\"");
        } else if (type.equals("HEADER.FIELDS.NOT")) {
          if (topLevel || (content.getContent() instanceof MimePart))
            fetchHeader((MimePart) content, split(breq.getPartIterator()), false);
          else
            getConn().print("\"\"");
        } else if (type.equals("TEXT")) {
          if (topLevel || (content.getContent() instanceof MimePart))
            fetchAll(breq, content);
          else
            getConn().print("\"\"");
        } else {
          int i = Integer.parseInt(type);
          Object part = content.getContent();

          if ((part instanceof String) && (i == 1) && (types.size() == 0) && topLevel) {
            fetchAll(breq, content);
          } else if (part instanceof MimeMultipart) {
            MimeMultipart mime = (MimeMultipart) part;

            if (i-1 < mime.getCount())
              fetchPart(breq, types, (MimeBodyPart) mime.getBodyPart(i-1), false);
            else
              getConn().print("\"\"");
          } else {
            getConn().print("\"\"");
          }
        }
      }
    } catch (IOException ioe) {
      throw new MailboxException(ioe);
    } catch (MessagingException me) {
      throw new MailboxException(me);
    }
  }

  protected void fetchHeader(MimePart msg) throws MailboxException {
    fetchHeader(msg, new String[0], false);
  }

  protected void fetchHeader(MimePart msg, String[] parts) throws MailboxException {
    fetchHeader(msg, parts, true);
  }

  protected void fetchHeader(MimePart msg, String[] parts, boolean exclude) throws MailboxException {
    try {
      Enumeration headers;

      if (exclude) {
        headers = msg.getMatchingHeaderLines(parts);
      } else {
        headers = msg.getNonMatchingHeaderLines(parts);
      }

      String result = collapse(headers) + "\r\n";
      
      getConn().print("{" + result.length() + "}\r\n");
      getConn().print(result);
    } catch (MessagingException me) {
      throw new MailboxException(me);
    }
  }

  public void fetchAll(BodyPartRequest breq, Object data) throws MailboxException {
    try {
      if (data instanceof String) {
        String content = getRange(breq, "" + data);

        if (content.equals("")) {
          getConn().print("\"\"");
        } else {
          getConn().print("{" + content.length() + "}\r\n");
          getConn().print(content);
        }
      } else if (data instanceof MimeBodyPart) {
        MimeBodyPart mime = (MimeBodyPart) data;

        InputStream stream = mime.getRawInputStream();
        StringWriter writer = new StringWriter();

        StreamUtils.copy(new InputStreamReader(stream), writer);

        String content = getRange(breq, writer.toString());
        getConn().print("{" + content.length() + "}\r\n");
        getConn().print(content);
      } else if (data instanceof MimeMultipart) {
        MimeMultipart mime = (MimeMultipart) data;

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        mime.writeTo(stream);

        String content = getRange(breq, stream.toString());
        getConn().print("{" + content.length() + "}\r\n");
        getConn().print(content);
      } else {
        getConn().print("NIL");
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
