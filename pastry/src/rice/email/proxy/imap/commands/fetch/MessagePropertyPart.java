package rice.email.proxy.imap.commands.fetch;

import rice.email.proxy.mail.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.util.*;

import java.io.*;

import java.util.Arrays;
import java.util.List;

import javax.mail.*;
import javax.mail.internet.*;


public class MessagePropertyPart extends FetchPart {
  
    List supportedParts = Arrays.asList(new Object[]  {
      "ALL", "FAST", "FULL", "BODY", "BODYSTRUCTURE", "ENVELOPE",
      "FLAGS", "INTERNALDATE", "UID"
    });

    public boolean canHandle(Object req) {
        return supportedParts.contains(req);
    }

    public void fetch(StoredMessage msg, Object part)  {
      try {
        getConn().print(part + " ");

        if ("ALL".equals(part)) {
          fetch(msg, "FLAGS");
          getConn().print(" ");
          fetch(msg, "INTERNALDATE");
          getConn().print(" ");
          fetch(msg, "RFC822.SIZE");
          getConn().print(" ");
          fetch(msg, "ENVELOPE");
        } else if ("FAST".equals(part)) {
          fetch(msg, "FLAGS");
          getConn().print(" ");
          fetch(msg, "INTERNALDATE");
          getConn().print(" ");
          fetch(msg, "RFC822.SIZE");
        } else if ("FULL".equals(part)) {
          fetch(msg, "FLAGS");
          getConn().print(" ");
          fetch(msg, "INTERNALDATE");
          getConn().print(" ");
          fetch(msg, "RFC822.SIZE");
          getConn().print(" ");
          fetch(msg, "ENVELOPE");
          getConn().print(" ");
          fetch(msg, "BODY");
        } else if ("BODY".equals(part)) {
          getConn().print(fetchBodystructure(msg.getMessage().getMessage(), false));
        } else if ("BODYSTRUCTURE".equals(part)) {
          getConn().print(fetchBodystructure(msg.getMessage().getMessage(), true));
        } else if ("ENVELOPE".equals(part)) {
          getConn().print(fetchEnvelope(msg.getMessage().getMessage()));
        } else if ("FLAGS".equals(part)) {
          fetchFlags(msg);
        } else if ("INTERNALDATE".equals(part)) {
          fetchInternaldate(msg);
        } else if ("RFC822.SIZE".equals(part)) {
          fetchSize(msg);
        } else if ("UID".equals(part)) {
          fetchUID(msg);
        }
      } catch (Exception me) {
        System.out.println("Exception " + me + " thrown while fetching " + part);
        me.printStackTrace();
      }
    }
    
    String fetchBodystructure(MimePart mime, boolean bodystructure) throws MailboxException {
      try {
        Object data = mime.getContent();
        String result = "";
        
        if (data instanceof String) {
          String content = (String) data;

          String[] type = mime.getHeader("Content-Type");
          String contentType = "\"TEXT\" \"PLAIN\" (\"CHARSET\" \"US-ASCII\")";
          
          if ((type != null) && (type.length > 0)) {
            contentType = handleContentType(type[0]);
          }

          String encoding = getHeader(mime, "Content-Transfer-Encoding").toUpperCase();

          if (encoding.equals("NIL"))
            encoding = "\"7BIT\"";

          StringWriter writer = new StringWriter();

          if (mime instanceof MimeBodyPart)
            StreamUtils.copy(new InputStreamReader(((MimeBodyPart) mime).getRawInputStream()), writer);
          else
            StreamUtils.copy(new InputStreamReader(mime.getInputStream()), writer);
            
          result = "(" + contentType + " NIL NIL " + encoding + " " +
                   writer.toString().length() + " " + countLines(writer.toString());

          if (bodystructure) {
            result += " " + getHeader(mime, "Content-MD5");

            String disposition = getHeader(mime, "Content-Disposition");

            if (! disposition.equals("NIL"))
              result += " (" + handleContentType(disposition, false, false) + ") ";
            else
              result += " NIL ";

            result += getHeader(mime, "Content-Language");
          }

          result += ")";
        } else if (data instanceof MimeMultipart) {
          MimeMultipart part = (MimeMultipart) data;
          result = parseMimeMultipart(part, bodystructure);
        } else {
          String content = "" + data;
          result = "{" + content.length() + "}\r\n" +content;
        }

        return result;
      } catch (MessagingException e) {
        throw new MailboxException(e);
      } catch (IOException ioe) {
        throw new MailboxException(ioe);
      }
    }

    private String parseMimeMultipart(MimeMultipart mime, boolean bodystructure) throws MessagingException, MailboxException {
      try {
        String result = "(";

        for (int i=0; i<mime.getCount(); i++) {
          MimeBodyPart part = (MimeBodyPart) mime.getBodyPart(i);

          if (part.getContent() instanceof MimeMultipart) {
            result += parseMimeMultipart((MimeMultipart) part.getContent(), bodystructure);
          } else {
            result += parseMimeBodyPart(part, bodystructure);
          }
        }

        if (mime.getContentType().toLowerCase().indexOf("multipart/") >= 0) {
          String type = handleContentType(mime.getContentType());

          type = type.substring(type.indexOf(" ") + 1);

          if (! bodystructure) {
            type = type.substring(0, type.indexOf(" ("));
          } else {
            type += " NIL NIL";
          }
            
          result += " " + type;
        } else {
          result += " \"MIXED\"";
        }
        
        return result + ")";
      } catch (IOException ioe) {
        throw new MailboxException(ioe);
      }
    }

    private String parseMimeBodyPart(MimeBodyPart mime, boolean bodystructure) throws MessagingException {
      try {
        String result = "(";

        result += handleContentType(mime.getContentType()) + " ";

        String encoding = ("\"" + mime.getEncoding() + "\"").toUpperCase();

        if (encoding.equals("\"NULL\"")) {
          encoding = "\"7BIT\"";
        }

        String id = "\"" + mime.getContentID() + "\"";

        if (id.equals("\"null\"")) {
          id = "NIL";
        }

        String description = "\"" + mime.getDescription() + "\"";

        if (description.equals("\"null\"")) {
          description = "NIL";
        }

        StringWriter writer = new StringWriter();
        StreamUtils.copy(new InputStreamReader(mime.getRawInputStream()), writer);
        
        result +=  id + " " + description + " " + encoding + " " + writer.toString().length();

        if (handleContentType(mime.getContentType().toUpperCase()).indexOf("\"TEXT\" \"") >= 0) {
          result += " " + countLines(writer.toString());
        }

        if (handleContentType(mime.getContentType().toUpperCase()).startsWith("\"MESSAGE\" \"RFC822\"")) {
          javax.mail.internet.MimeMessage message = (javax.mail.internet.MimeMessage) mime.getContent();
          result += " " + fetchEnvelope(message) + " " + fetchBodystructure(message, bodystructure) + " " +
            countLines(writer.toString());
        }

        if (bodystructure) {
          String md5 = mime.getContentMD5();
          String disposition = mime.getHeader("Content-Disposition", ";");
          String language[] = mime.getContentLanguage();

          if (md5 == null)
            result += " NIL";
          else
            result += " \"" + md5 + "\"";

          if (disposition != null)
            result += " (" + handleContentType(disposition, false, false) + ")";
          else
            result += " NIL";

          if (language == null)
            result += " NIL";
          else
            result += " \"" + language[0] + "\"";
        }

        result += ")";

        return result;
      } catch (IOException e) {
        throw new MessagingException();
      } catch (MailboxException e) {
        throw new MessagingException();
      }
    }

    private String handleContentType(String type) {
      return handleContentType(type, true);
    }

    private String handleContentType(String type, boolean includeSubType) {
      return handleContentType(type, includeSubType, true);
    }
    
    private String handleContentType(String type, boolean includeSubType, boolean insertDefaultChartype) {
      String[] props = type.split(";");

      String result = parseContentType(props[0], includeSubType) + " ";

      String propText = "";

      for (int i=1; i<props.length; i++) {
        String thisProp = parseBodyParameter(props[i]);

        if (! thisProp.equals("NIL"))
          propText += thisProp + " ";
      }

      if (propText.equals(""))
        if (insertDefaultChartype)
          result += "(\"CHARSET\" \"US-ASCII\")";
        else
          result += "NIL";
      else
        result += "(" + propText.trim() + ")";

      return result;
    }

    private String parseContentType(String type, boolean includeSubType) {
      if (type.matches("\".*\"")) {
        type = type.substring(1, type.length()-1);
      }
      
      String mainType = "\"" + type + "\"";
      String subType = "NIL";

      if (type.indexOf("/") != -1) {
        mainType = "\"" + type.substring(0,type.indexOf("/")) + "\"";

        if (type.indexOf(";") != -1) {
          subType = "\"" + type.substring(type.indexOf("/") + 1, type.indexOf(";")) + "\"";
        } else {
          subType = "\"" + type.substring(type.indexOf("/") + 1) + "\"";
        }
      }

      if (includeSubType)
        return mainType.toUpperCase() + " " + subType.toUpperCase();
      else
        return mainType.toUpperCase();
    }
    
    private String parseBodyParameter(String content) {
      content = content.trim();
      String result = "NIL";

      if (content.indexOf("=") >= 0) {
        String name = content.substring(0, content.indexOf("=")).toUpperCase();
        String value = content.substring(content.indexOf("=") + 1);

        if (value.matches("\".*\"")) {
          value = value.substring(1, value.length()-1);
        }

        result = "\"" + name.toUpperCase() + "\" \"" + value + "\"";
      } 

      return result;
    }

    private int countLines(String string) {
      int len = (string.split("\n")).length;

      if (! string.endsWith("\n")) {
        len--;
      }

      return len;
    }

    void fetchSize(StoredMessage msg) throws MailboxException, MailException {
        getConn().print("" + msg.getMessage().getSize());
    }

    void fetchUID(StoredMessage msg) throws MailboxException {
        getConn().print("" + msg.getUID());
    }

    void fetchID(StoredMessage msg) throws MailboxException {
      getConn().print("\"" + msg.getSequenceNumber() + "\"");
    }

    void fetchFlags(StoredMessage msg) throws MailboxException {
        getConn().print(msg.getFlagList().toFlagString());
    }

    void fetchInternaldate(StoredMessage msg) throws MailboxException {
        getConn().print("\"" + msg.getMessage().getInternalDate() + "\"");
    }

    private String addresses(InternetAddress[] addresses) {
      if ((addresses != null) && (addresses.length > 0)) {
        String result = "(";

        for (int i=0; i<addresses.length; i++) {
          result += address(addresses[i]);
        }

        return result + ")";
      } else {
        return "NIL";
      }
    }

    private String address(InternetAddress address) {
      String personal = address.getPersonal();

      if (personal == null)
        personal = "NIL";
      else
        personal = "\"" + personal + "\"";

      String emailAddress = address.getAddress();
      String user = "NIL";
      String server = "NIL";

      if (emailAddress != null) {
        if (emailAddress.indexOf("@") >= 0) {
          user = "\"" + emailAddress.substring(0, address.getAddress().indexOf("@")) + "\"";
          server = "\"" + emailAddress.substring(address.getAddress().indexOf("@") + 1) + "\"";
        } else {
          user = "\"" + emailAddress + "\"";
        }
      }

      return "(" + personal + " NIL " + user + " " + server + ")";
    }

    private String collapse(String[] addresses) {
      if ((addresses == null) || (addresses.length == 0)) return "";
      if (addresses.length == 1) return addresses[0];
      
      String result = addresses[0];

      for (int i=1; i<addresses.length; i++) {
        result += ", " + addresses[i];
      }

      return result;
    }

    private String getHeader(MimePart part, String header) throws MailboxException {
      try {
        String[] result = part.getHeader(header);

        if ((result != null) && (result.length > 0)) {
          String text = result[0].replaceAll("\\n", "").replaceAll("\\r", "");

          if (text.indexOf("\"") == -1)
            return "\"" + text + "\"";
          else
            return "{" + text.length() + "}\r\n" + text; 
      } else
          return "NIL";
      } catch (MessagingException e) {
        throw new MailboxException(e);
      }
    }
    
    public String fetchEnvelope(MimePart part) throws MailboxException {
      try {
        String result = "(" + getHeader(part, "Date") + " " + getHeader(part, "Subject") + " ";

        //from, sender, reply-to, to, cc, bcc, in-reply-to, and message-id.
        InternetAddress[] from = InternetAddress.parse(collapse(part.getHeader("From")));
        InternetAddress[] sender = InternetAddress.parse(collapse(part.getHeader("Sender")));
        InternetAddress[] replyTo = InternetAddress.parse(collapse(part.getHeader("Reply-To")));
        InternetAddress[] to = InternetAddress.parse(collapse(part.getHeader("To")));
        InternetAddress[] cc = InternetAddress.parse(collapse(part.getHeader("Cc")));
        InternetAddress[] bcc = InternetAddress.parse(collapse(part.getHeader("Bcc")));

        if (addresses(sender).equals("NIL"))
          sender = from;

        if (addresses(replyTo).equals("NIL"))
          replyTo = from;
        
        result += addresses(from) + " ";
        result += addresses(sender) + " ";
        result += addresses(replyTo) + " ";
        result += addresses(to) + " ";
        result += addresses(cc) + " ";
        result += addresses(bcc) + " ";
        result += getHeader(part, "In-Reply-To") + " ";
        result += getHeader(part, "Message-ID") + ")";

        return result;
      } catch (AddressException ae) {
        throw new MailboxException(ae);
      } catch (MessagingException me) {
        throw new MailboxException(me);
      }
    }
}
