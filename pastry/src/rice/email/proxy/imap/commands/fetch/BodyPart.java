package rice.email.proxy.imap.commands.fetch;

import rice.email.proxy.mail.*;

import rice.email.proxy.mailbox.*;
import rice.email.proxy.util.*;

import java.io.*;

import java.util.Iterator;

import javax.mail.*;
import javax.mail.internet.*;


public class BodyPart
    extends FetchPart
{
    public boolean canHandle(Object req)
    {

        return req instanceof BodyPartRequest;
    }

  private String toSentenceCase(String s) {
    return s.substring(0,1).toUpperCase() + s.substring(1, s.length());
  }

    public void fetch(StoredMessage msg, Object part) throws MailboxException
    {
        try
        {
          BodyPartRequest req = (BodyPartRequest) part;
          getConn().print(req.toString() + " ");

          if (req.getType().equals("HEADER.FIELDS")) {
            if (req.getPartIterator().hasNext()) {
              Iterator i = req.getPartIterator();
              String data = "";

              while (i.hasNext()) {
                String next = (String) i.next();
                String[] headers = msg.getMessage().getHeader(next);

                for (int j=0; j<headers.length; j++) {
                  data += toSentenceCase(next) + ": " + headers[j] + "\n";
                }
              }

              data += "\n";

              getConn().print("{" + data.length() + "}\r\n" + data);
            } else {
              fetchHeaders(msg, req);
            }
          } else if (req.getType().equals("HEADER")) {
            fetchHeaders(msg, req);
          } else {
            Object data = msg.getMessage().getContent();

            int i = 1;

            try {
              i = Integer.parseInt(req.getType());
            } catch (NumberFormatException e) {
            }

            if (data instanceof String) {
              System.out.println("Found a string...");

              String content = "" + data;
              getConn().print("{" + content.length() + "}\r\n");
              getConn().print(content);
            } else if (data instanceof MimeMultipart) {
              System.out.println("Found a multipart...");

              MimeMultipart mime = (MimeMultipart) data;
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
          }
        } catch (IOException ioe) {
          throw new MailboxException(ioe);
        } catch (MailException me) {
          throw new MailboxException(me);
        } catch (MessagingException me) {
          throw new MailboxException(me);
        }
    }

    void fetchHeaders(StoredMessage msg, BodyPartRequest req)
               throws MailboxException
    {
        // Iterator headerNames = req.getPartIterator();
        try
        {
          if (req.getPartIterator().hasNext()) {
            System.out.println("MONKEYS IN THE CODE!!!!!!!!!");
          } else {
            String header = msg.getMessage().getHeader();
            getConn().print("{" + header.length() + "}\r\n");
            getConn().print(header);
          }
        }
        catch (MailException me)
        {
            throw new MailboxException(me);
        }

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
}
