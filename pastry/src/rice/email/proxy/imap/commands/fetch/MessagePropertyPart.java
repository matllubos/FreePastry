package rice.email.proxy.imap.commands.fetch;

import rice.email.proxy.mail.*;

import rice.email.proxy.mailbox.*;

import java.io.*;

import java.util.Arrays;
import java.util.List;

import javax.mail.*;
import javax.mail.internet.*;


public class MessagePropertyPart
    extends FetchPart
{
    List supportedParts = Arrays.asList(
                                  new Object[] 
    {
        "RFC822.PEEK", "RFC822", "RFC822.HEADER", "RFC822.SIZE", "RFC822.TEXT",
        "UID", "FLAGS", "INTERNALDATE", "ALL", "ENVELOPE", "BODY", "BODYSTRUCTURE"
    });

    public boolean canHandle(Object req)
    {

        return supportedParts.contains(req);
    }

    public void fetch(StoredMessage msg, Object part)
    {
        try
        {
            if ("RFC822.PEEK".equals(part))
                part = "RFC822";
          
            getConn().print(part + " ");
            if ("RFC822.HEADER".equals(part))
                bodyHeader(msg);
            else if ("RFC822.SIZE".equals(part))
                bodySize(msg);
            else if ("UID".equals(part))
                msgUID(msg);
            else if ("FLAGS".equals(part))
                msgFlags(msg);
            else if ("INTERNALDATE".equals(part))
                internalDate(msg);
            else if ("RFC822".equals(part))
                entireMsg(msg);
            else if ("ENVELOPE".equals(part))
                envelope(msg);
            else if ("BODY".equals(part))
                body(msg);
            else if ("BODYSTRUCTURE".equals(part))
                body(msg);
            else if ("RFC822.TEXT".equals(part))
                text(msg);
            else if ("ALL".equals(part)) {
                fetch(msg, "FLAGS");
                fetch(msg, "INTERNALDATE");
                fetch(msg, "RFC822.SIZE");
            } 
                
        }
        catch (Exception me)
        {
            System.out.println(
                    "PROGRAMMING LAZINESS NEEDS TO BE FIXED NOW");
            me.printStackTrace();
        }
    }

    void bodyHeader(StoredMessage msg) throws MailboxException {
      try {
        String header = msg.getMessage().getHeader();
        getConn().print("{" + header.length() + "}\r\n");
        getConn().print(header);
      } catch (MailException me) {
        throw new MailboxException(me);
      }
    }

    void body(StoredMessage msg) throws MailboxException {
      try {
        Object data = msg.getMessage().getContent();

        if (data instanceof String) {
          String content = (String) data;
          String[] lines = content.split("\n");
          getConn().print("(\"TEXT\" \"PLAIN\" NIL NIL NIL \"7BIT\" " + content.length() +
                          " " + lines.length + ")");
        } else if (data instanceof MimeMultipart) {
          MimeMultipart mime = (MimeMultipart) data;
          String content = parseMimeMultipart(mime);
          getConn().print(content);
        } else {
          String content = "" + data;
          getConn().print("{" + content.length() + "}\r\n");
          getConn().print(content);
        }
      } catch (MessagingException e) {
        throw new MailboxException(e);
      } catch (MailException me) {
        throw new MailboxException(me);
      }
    }

    private String parseMimeMultipart(MimeMultipart mime) throws MessagingException {
      String result = "(";

      for (int i=0; i<mime.getCount(); i++) {
        result += parseMimeBodyPart((MimeBodyPart) mime.getBodyPart(i));
      }

      result += " \"MIXED\")";

      return result;
    }

    private String parseMimeBodyPart(MimeBodyPart mime) throws MessagingException {
      String result = "(";

      String type = mime.getContentType().toUpperCase();
      String mainType = "\"" + type + "\"";
      String subType = "NIL";

      if (type.indexOf("/") != -1) {
        mainType = "\"" + type.substring(0,type.indexOf("/")) + "\"";

        if (type.indexOf(";") != -1) {
          subType = "\"" + type.substring(type.indexOf("/") + 1, type.indexOf(";")) + "\"";
        }
      }

      String charset = parseContentType(type, "charset");
      String name = parseContentType(type, "name");

      result += mainType + " " + subType + " ";

      result += "(\"CHARSET\" \"" + charset + "\"";

      if (name.equals("NIL")) {
        result += ") ";
      } else {
        result += " \"NAME\" " + name + ") ";
      }

      String encoding = "\"" + mime.getEncoding() + "\"";

      if (encoding.equals("\"null\"")) {
        encoding = "\"7BIT\"";
      }

      result += "NIL NIL " + encoding + " " + mime.getSize() + " " + mime.getLineCount() + ")";

      return result;
    }

    private String parseContentType(String content, String field) {
      String temp = content.substring(0).toUpperCase();
      field = field.toUpperCase();

      if (temp.indexOf(field + "=") != -1) {
        if (temp.indexOf(";", temp.indexOf(field + "=")) != -1) {
          return content.substring(temp.indexOf(field + "=") + field.length() + 1,
                                   temp.indexOf(";", temp.indexOf(field + "=")));
        } else {
          return content.substring(temp.indexOf(field + "=") + field.length() + 1);
        }
      } else {
        return "NIL";
      }
    }    
 
    void text(StoredMessage msg)
      throws MailboxException, MailException
    {
      try {
        Object data = msg.getMessage().getContent();

        if (data instanceof String) {
          String content = (String) data;
          getConn().print("{" + content.length() + "}\r\n");
          getConn().print(content);
        } else if (data instanceof MimeMultipart) {
          MimeMultipart mime = (MimeMultipart) data;

          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          mime.writeTo(baos);

          String content = new String(baos.toByteArray());
          getConn().print("{" + content.length() + "}\r\n");
          getConn().print(content);
        } else {
          String content = "" + data;
          getConn().print("{" + content.length() + "}\r\n");
          getConn().print(content);
        }
      } catch (IOException e) {
        throw new MailboxException(e);
      } catch (MessagingException e) {
        throw new MailboxException(e);
      }
    }

    void bodySize(StoredMessage msg)
           throws MailboxException, MailException
    {
        getConn().print("" + msg.getMessage().getSize());
    }

    void msgUID(StoredMessage msg)
         throws MailboxException
    {
        getConn().print("\"" + msg.getUID() + "\"");
    }

    void msgFlags(StoredMessage msg)
           throws MailboxException
    {
        getConn().print(msg.getFlagList().toFlagString());
    }

    void internalDate(StoredMessage msg)
               throws MailboxException
    {
        getConn().print(
                '"' + msg.getMessage().getInternalDate() + '"');
    }

    public void entireMsg(StoredMessage msg)
                   throws MailboxException
    {
        try
        {
            Reader contents = msg.getMessage().getContents();
            getConn().print(
                    "{" + msg.getMessage().getSize() + "}\r\n");
            getConn().print(contents);
        }
        catch (MailException me)
        {
            throw new MailboxException(me);
        }
        catch (IOException ioe)
        {
            throw new MailboxException(ioe);
        }
    }

    private void addresses(InternetAddress[] addresses) {
      if ((addresses != null) && (addresses.length > 0)) {
        getConn().print("(");

        for (int i=0; i<addresses.length; i++) {
          address(addresses[i]);
        }

        getConn().print(")");
      } else {
        getConn().print("NIL");
      }
    }

    private void address(InternetAddress address) {
      String personal = address.getPersonal();

      if (personal == null)
        personal = "NIL";
      else
        personal = "\"" + personal + "\"";

      String emailAddress = address.getAddress();
      String user = null;
      String server = null;

      if (emailAddress == null) {
        user = "NIL";
        server = "NIL";
      } else {
        user = "\"" + emailAddress.substring(0, address.getAddress().indexOf("@")) + "\"";
        server = "\"" + emailAddress.substring(address.getAddress().indexOf("@") + 1) + "\"";
      }

      getConn().print("(" + personal + " NIL " + user + " " + server + ")");
    }

    private String collapse(String[] addresses) {
      if (addresses == null) return "";
      if (addresses.length == 1) return addresses[0];
      
      String result = addresses[0];

      for (int i=1; i<addresses.length; i++) {
        result += ", " + addresses[i];
      }

      return result;
    }
    
    public void envelope(StoredMessage msg)
                   throws MailboxException
    {
      try {
        getConn().print("(");
        internalDate(msg);

        String[] subject = msg.getMessage().getHeader("Subject");

        if ((subject != null) && (subject.length > 0))
            getConn().print(" \"" + subject[0] + "\" ");
        else
            getConn().print(" NIL ");

        //from, sender, reply-to, to, cc, bcc, in-reply-to, and message-id.
        InternetAddress[] from = InternetAddress.parse(collapse(msg.getMessage().getHeader("From")));
        InternetAddress[] sender = InternetAddress.parse(collapse(msg.getMessage().getHeader("Sender")));
        InternetAddress[] replyTo = InternetAddress.parse(collapse(msg.getMessage().getHeader("Reply-To")));
        InternetAddress[] to = InternetAddress.parse(collapse(msg.getMessage().getHeader("To")));
        InternetAddress[] cc = InternetAddress.parse(collapse(msg.getMessage().getHeader("Cc")));
        InternetAddress[] bcc = InternetAddress.parse(collapse(msg.getMessage().getHeader("Bcc")));
        String[] inReplyTo = msg.getMessage().getHeader("In-Reply-To");
        
        addresses(from);
        getConn().print(" ");
        addresses(sender);
        getConn().print(" ");
        addresses(replyTo);
        getConn().print(" ");
        addresses(to);
        getConn().print(" ");
        addresses(cc);
        getConn().print(" ");
        addresses(bcc);
        getConn().print(" ");

        if ((inReplyTo == null) || (inReplyTo.length == 0)) {
            getConn().print("NIL ");
        } else {
            getConn().print("\"" + inReplyTo[0] + "\" ");
        }
        msgUID(msg);
        getConn().print(")");
      } catch (MailException me) {
        throw new MailboxException(me);
      } catch (AddressException ae) {
        throw new MailboxException(ae);
      }
    }
}