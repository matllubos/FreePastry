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

  private String[] seperate(String part) {
    return part.split("\\.");
  }

  public void fetch(StoredMessage msg, Object part) throws MailboxException {
    getConn().print(part.toString() + " ");

    fetchHandler(msg, part);
  }

  protected void fetchHandler(StoredMessage msg, Object part) throws MailboxException {
    if (part instanceof RFC822PartRequest) {
      RFC822PartRequest rreq = (RFC822PartRequest) part;
      part = new BodyPartRequest();

      if (rreq.getType().equals("HEADER")) {
        ((BodyPartRequest) part).setType("HEADER");
        ((BodyPartRequest) part).setPeek(true);
      } else if (rreq.getType().equals("TEXT")) {
        ((BodyPartRequest) part).setType("TEXT");
      }

      fetchHandler(msg, part);
    } else if (part instanceof BodyPartRequest) {
      BodyPartRequest breq = (BodyPartRequest) part;

      if (breq.getType().equals("")) {
        fetchEntireMessage(msg);
      } else if (breq.getType().equals("HEADER")) {
        fetchHeader(msg);
      } else if (breq.getType().equals("HEADER.FIELDS")) {
        fetchHeader(msg, split(breq.getPartIterator()));
      } else if (breq.getType().equals("HEADER.FIELDS.NOT")) {
        fetchHeader(msg, split(breq.getPartIterator()), false);
      } else if (breq.getType().equals("TEXT")) {
        fetchMessagePart(msg, seperate("TEXT"));
      } else {
        fetchMessagePart(msg, seperate(breq.getType()));
      }

      if ((! breq.getPeek()) &&	(! msg.getFlagList().isSeen())) {
        msg.getFlagList().addFlag("\\SEEN");
        getConn().print(" ");
        
        FetchPart handler = FetchCommand.regestry.getHandler("FLAGS");
        handler.setConn(getConn());
        handler.fetch(msg, "FLAGS");
      }
    } else {
      getConn().print("NIL");
    }
  }

  protected void fetchHeader(StoredMessage msg) throws MailboxException {
    fetchHeader(msg, new String[0], false);
  }

  protected void fetchHeader(StoredMessage msg, String[] parts) throws MailboxException {
    fetchHeader(msg, parts, true);
  }

  protected void fetchHeader(StoredMessage msg, String[] parts, boolean exclude) throws MailboxException {
    try {
      Enumeration headers;

      if (exclude) {
        headers = msg.getMessage().getMatchingHeaderLines(parts);
      } else {
        headers = msg.getMessage().getNonMatchingHeaderLines(parts);
      }

      String result = collapse(headers);
      
      getConn().print("{" + result.length() + "}\r\n");
      getConn().print(result);
    } catch (MailException me) {
      throw new MailboxException(me);
    }
  }

  protected void fetchMessagePart(StoredMessage msg, String[] part) throws MailboxException {
    try {
      Object data = msg.getMessage().getContent();

      if (data instanceof String) {
        System.out.println("Found a string...");

        if ((part.length == 1) && (part[0].equals("TEXT") || part[0].equals("1"))) {
          String content = "" + data;
          getConn().print("{" + content.length() + "}\r\n");
          getConn().print(content);
        } else {
          getConn().print("NIL");
        }
      } else if (data instanceof MimeMultipart) {
        System.out.println("Found a multipart...");

        MimeMultipart mime = (MimeMultipart) data;

        // NEED TO DO RECURSIVE MESSAGE PARSING HERE...
        int i = Integer.parseInt(part[0]);
        
        MimeBodyPart thisPart = (MimeBodyPart) mime.getBodyPart(i-1);

        InputStream stream = thisPart.getInputStream();
        StringWriter writer = new StringWriter();

        StreamUtils.copy(new InputStreamReader(stream), writer);

        String content = writer.toString();
        getConn().print("{" + content.length() + "}\r\n");
        getConn().print(content);
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

  public void fetchEntireMessage(StoredMessage msg) throws MailboxException {
    try {
      Reader contents = msg.getMessage().getContents();
      String result = StreamUtils.toString(contents);
      result = result.replaceAll("\n", "\r\n");
      getConn().print( "{" + result.length() + "}\r\n");
      getConn().print(result);
    } catch (MailException me) {
      throw new MailboxException(me);
    } catch (IOException ioe) {
      throw new MailboxException(ioe);
    }
  }
}
