package rice.email.proxy.imap.commands.fetch;

import rice.*;
import rice.Continuation.*;

import rice.email.*;
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

    public String fetch(StoredMessage msg, Object part) throws MailboxException {
      return part + " " + fetchHandler(msg, part);
    }

    public String fetchHandler(StoredMessage msg, Object part) throws MailboxException {
      ExternalContinuation c = new ExternalContinuation();
      msg.getMessage().getContent(c);
      c.sleep();

      if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

      EmailMessagePart message = (EmailMessagePart) c.getResult();

      if ("ALL".equals(part)) {
        return fetch(msg, "FLAGS") + " " +
        fetch(msg, "INTERNALDATE") + " " +
        fetch(msg, "RFC822.SIZE") + " " +
        fetch(msg, "ENVELOPE");
      } else if ("FAST".equals(part)) {
        return fetch(msg, "FLAGS") + " " +
        fetch(msg, "INTERNALDATE") + " " +
        fetch(msg, "RFC822.SIZE");
      } else if ("FULL".equals(part)) {
        return fetch(msg, "FLAGS") + " " +
        fetch(msg, "INTERNALDATE") + " " +
        fetch(msg, "RFC822.SIZE") + " " +
        fetch(msg, "ENVELOPE") + " " +
        fetch(msg, "BODY");
      } else if ("BODY".equals(part)) {
        return fetchBodyStructure(message, false);
      } else if ("BODYSTRUCTURE".equals(part)) {
        return fetchBodyStructure(message, true);
      } else if ("ENVELOPE".equals(part)) {
        return fetchEnvelope(message);
      } else if ("FLAGS".equals(part)) {
        return fetchFlags(msg);
      } else if ("INTERNALDATE".equals(part)) {
        return fetchInternaldate(message);
      } else if ("RFC822.SIZE".equals(part)) {
        return fetchSize(message);
      } else if ("UID".equals(part)) {
        return fetchUID(msg);
      } else {
        throw new MailboxException("Unknown part type specifier");
      }
    }

    String fetchBodyStructure(EmailContentPart part, boolean bodystructure) throws MailboxException {
      if (part instanceof EmailMultiPart)
        return fetchBodyStructure((EmailMultiPart) part, bodystructure);
      else if (part instanceof EmailHeadersPart)
        return fetchBodyStructure((EmailHeadersPart) part, bodystructure);
      else if (part instanceof EmailMessagePart)
        return fetchBodyStructure((EmailMessagePart) part, bodystructure);
      else
        throw new MailboxException("Unrecognized part " + part);
    }

    String fetchBodyStructure(EmailMultiPart part, boolean bodystructure) throws MailboxException {
      ExternalContinuation c = new ExternalContinuation();
      part.getContent(c);
      c.sleep();

      if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

      EmailContentPart[] parts = (EmailContentPart[]) c.getResult();

      StringBuffer result = new StringBuffer();
      result.append("(");

      for (int i=0; i<parts.length; i++) {
        result.append(fetchBodyStructure(parts[i], bodystructure));
      }

      if (part.getType().toLowerCase().indexOf("multipart/") >= 0) {
        String type = handleContentType(part.getType());

        type = type.substring(type.indexOf(" ") + 1);

        if (! bodystructure) {
          type = type.substring(0, type.indexOf(" ("));
        } else {
          type += " NIL NIL";
        }

        result .append(" " + type);
      } else { 
        result.append(" \"MIXED\"");
      }

      return result.append(")").toString();
    }

    String fetchBodyStructure(EmailHeadersPart part, boolean bodystructure) throws MailboxException {
      ExternalContinuation c = new ExternalContinuation();
      part.getHeaders(c);
      c.sleep();

      if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

      InternetHeaders headers = getHeaders((EmailData) c.getResult());

      c = new ExternalContinuation();
      part.getContent(c);
      c.sleep();

      if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

      EmailContentPart content = (EmailContentPart) c.getResult();
      
      StringBuffer result = new StringBuffer();

      String contentHeader = getHeader(headers, "Content-Type", false);

      if (contentHeader == "NIL")
        contentHeader = "text/plain";
      
      String contentType = handleContentType(contentHeader);

      String encoding = getHeader(headers, "Content-Transfer-Encoding").toUpperCase();

      if (encoding.equals("NIL"))
        encoding = "\"7BIT\"";

      String id = getHeader(headers, "Content-ID");

      String description = getHeader(headers, "Content-Description");

      result.append("(" + contentType + " " + id + " " + description + " " + encoding + " " + part.getSize());

      if (contentType.indexOf("TEXT") >= 0) {
        result.append(" " + ((EmailSinglePart) content).getLines());
      }

      if (content instanceof EmailMessagePart) {
        EmailMessagePart message = (EmailMessagePart) content;
        result.append(" " + fetchEnvelope(message) + " " + fetchBodyStructure(message, bodystructure) + " 0");
      }

      if (bodystructure) {
        result.append(" " + getHeader(headers, "Content-MD5"));

        String disposition = getHeader(headers, "Content-Disposition", false);

        if (! disposition.equals("NIL"))
          result.append(" (" + handleContentType(disposition, false, false) + ") ");
        else
          result.append(" NIL ");

        result.append(getHeader(headers, "Content-Language"));
      }
    
      return result.append(")").toString();      
    }

    String fetchBodyStructure(EmailMessagePart part, boolean bodystructure) throws MailboxException {
      ExternalContinuation c = new ExternalContinuation();
      part.getContent(c);
      c.sleep();

      if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

      EmailContentPart data = (EmailContentPart) c.getResult();

      if (data instanceof EmailMultiPart) {
        return fetchBodyStructure((EmailMultiPart) data, bodystructure);
      } else {
        return fetchBodyStructure((EmailHeadersPart) part, bodystructure);
      }
    }
    /*
    String fetchBodyStructure(EmailMessagePart part, boolean bodystructure) throws MailboxException {
      try {
        ExternalContinuation c = new ExternalContinuation();
        part.getHeaders(c);
        c.sleep();

        if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

        InternetHeaders headers = new InternetHeaders(new ByteArrayInputStream((EmailData) c.getResult()).getData());

        c = new ExternalContinuation();
        part.getContent(c);
        c.sleep();

        if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }
        
        EmailContentPart data = (EmailData) c.getResult();
        String result = "";

        if (data instanceof EmailMultiPart) {
          result = parseMimeMultipart((EmailMultiPart) data, bodystructure);
        } else {
          String[] type = headers.getHeader("Content-Type");
          String contentType = "\"TEXT\" \"PLAIN\" (\"CHARSET\" \"US-ASCII\")";
          
          if ((type != null) && (type.length > 0)) {
            contentType = handleContentType(type[0]);
          }

          String encoding = getHeader(headers, "Content-Transfer-Encoding").toUpperCase();

          if (encoding.equals("NIL"))
            encoding = "\"7BIT\"";
            
          result = "(" + contentType + " NIL NIL " + encoding + " " + part.getSize() + " 0";

          if (bodystructure) {
            result += " " + getHeader(headers, "Content-MD5");

            String disposition = getHeader(headers, "Content-Disposition");

            if (! disposition.equals("NIL"))
              result += " (" + handleContentType(disposition, false, false) + ") ";
            else
              result += " NIL ";

            result += getHeader(headers, "Content-Language");
          }

          result += ")";
        }

        return result;
      } catch (MessagingException e) {
        throw new MailboxException(e);
      } catch (IOException ioe) {
        throw new MailboxException(ioe);
      }
    }

    private String parseMimeMultipart(EmailMultiPart part, boolean bodystructure) throws MessagingException, MailboxException {
      try {
        ExternalContinuation c = new ExternalContinuation();
        part.getContent(c);
        c.sleep();

        if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

        EmailContentPart[] parts = (EmailContentPart[]) c.getResult();
        
        String result = "(";

        for (int i=0; i<parts.length; i++) {
          EmailContentPart thisPart = (EmailContentPart) parts[i];

          if (thisPart instanceof EmailMultiPart) {
            result += parseMimeMultipart((EmailMultiPart) thisPart, bodystructure);
          } else {
            result += parseMimeBodyPart(thisPart, bodystructure);
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

    private String parseMimeBodyPart(EmailContentPart mime, boolean bodystructure) throws MessagingException {
      try {
        String result = "(";

        result += handleContentType(mime.getContentType()) + " ";

        String encoding = ("\"" + mime.getEncoding() + "\"").toUpperCase();

        if (encoding.equals("\"NULL\"") || encoding.equals("\"\"")) {
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
    } */

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

    String fetchSize(EmailMessagePart message) throws MailboxException {
      return "" + message.getSize();
    }

    String fetchUID(StoredMessage msg) throws MailboxException {
      return "" + msg.getUID();
    }

    String fetchID(StoredMessage msg) throws MailboxException {
      return "\"" + msg.getSequenceNumber() + "\"";
    }

    String fetchFlags(StoredMessage msg) throws MailboxException {
      return msg.getFlagList().toFlagString();
    }

    String fetchInternaldate(EmailMessagePart message) throws MailboxException {
      ExternalContinuation c = new ExternalContinuation();
      message.getHeaders(c);
      c.sleep();

      if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

      InternetHeaders headers = getHeaders((EmailData) c.getResult());

      return getHeader(headers, "Date");
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
        personal = "\"" + personal.replaceAll("\"", "'") + "\"";

      String emailAddress = address.getAddress().replaceAll("\"", "'");
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

    private InternetHeaders getHeaders(EmailData data) throws MailboxException {
      try {
        return new InternetHeaders(new ByteArrayInputStream(data.getData()));
      } catch (MessagingException e) {
        throw new MailboxException(e);
      }
    }
    private String getHeader(InternetHeaders headers, String header) throws MailboxException {
      return getHeader(headers, header, true);
    }
    
    private String getHeader(InternetHeaders headers, String header, boolean format) throws MailboxException {
      String[] result = headers.getHeader(header);

      if ((result != null) && (result.length > 0)) {
        String text = result[0].replaceAll("\\n", "").replaceAll("\\r", "");

        if (! format)
          return text;

        text = text.replaceAll("\"", "'");
        
        if (text.indexOf("\"") == -1)
          return "\"" + text + "\"";
        else
          return "{" + text.length() + "}\r\n" + text;
      }

      return "NIL";
    }
    
    public String fetchEnvelope(EmailHeadersPart part) throws MailboxException {
      try {
        ExternalContinuation c = new ExternalContinuation();
        part.getHeaders(c);
        c.sleep();

        if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

        InternetHeaders headers = getHeaders((EmailData) c.getResult());

        StringBuffer result = new StringBuffer();

        //from, sender, reply-to, to, cc, bcc, in-reply-to, and message-id.
        InternetAddress[] from = InternetAddress.parse(collapse(headers.getHeader("From")));
        InternetAddress[] sender = InternetAddress.parse(collapse(headers.getHeader("Sender")));
        InternetAddress[] replyTo = InternetAddress.parse(collapse(headers.getHeader("Reply-To")));
        InternetAddress[] to = InternetAddress.parse(collapse(headers.getHeader("To")));
        InternetAddress[] cc = InternetAddress.parse(collapse(headers.getHeader("Cc")));
        InternetAddress[] bcc = InternetAddress.parse(collapse(headers.getHeader("Bcc")));

        if (addresses(sender).equals("NIL"))
          sender = from;

        if (addresses(replyTo).equals("NIL"))
          replyTo = from;

        result.append("(" + getHeader(headers, "Date") + " ");
        result.append(getHeader(headers, "Subject") + " ");
        result.append(addresses(from) + " ");
        result.append(addresses(sender) + " ");
        result.append(addresses(replyTo) + " ");
        result.append(addresses(to) + " ");
        result.append(addresses(cc) + " ");
        result.append(addresses(bcc) + " ");
        result.append(getHeader(headers, "In-Reply-To") + " ");
        result.append(getHeader(headers, "Message-ID") + ")");

        return result.toString();
      } catch (AddressException ae) {
        throw new MailboxException(ae);
      }
    }
}
