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

    public void fetch(StoredMessage msg, Object part)
    {
        try
        {
            BodyPartRequest req = (BodyPartRequest) part;
            getConn().print(req.toString() + " ");
            if (req.getType().equals("HEADER.FIELDS")) {
                fetchHeaders(msg, req);
            } else {
              Object data = msg.getMessage().getContent();

              try {
                int i = Integer.parseInt(req.getType());

                if (data instanceof String) {
                  System.out.println("Found a string...");
                  
                  String content = "" + data;
                  getConn().print("{" + content.length() + "}\r\n");
                  getConn().print(content);
                } else if (data instanceof MimeMultipart) {
                  System.out.println("Found a multipart...");
                  
                  MimeMultipart mime = (MimeMultipart) data;
                  MimeBodyPart thisPart = (MimeBodyPart) mime.getBodyPart(i-1);

            //      ByteArrayOutputStream baos = new ByteArrayOutputStream();
            //      thisPart.writeTo(baos);

                //  Object result = thisPart.getContent(); //new String(baos.toByteArray());

                  InputStream stream = thisPart.getInputStream();
                  StringWriter writer = new StringWriter();

                  StreamUtils.copy(new InputStreamReader(stream), writer);

                  String content = writer.toString();
                  getConn().print("{" + content.length() + "}\r\n");
                  getConn().print(content);
                  
                  /*
                  if (result instanceof String) {
                    System.out.println("It's a String!" + mime.getCount() + " " + );
                    String content = (String) result;
                    getConn().print("{" + content.length() + "}\r\n");
                    getConn().print(content);
                  } else {
                    System.out.println("It's a " + result.getClass().getName());
                  } */
                } else {
                  getConn().print("NIL");
                }
              } catch (NumberFormatException e) {
                getConn().print("NIL");
              }
            }
        }
        catch (Exception me)
        {
            System.out.println(
                    "PROGRAMMING LAZINESS NEEDS TO BE FIXED NOW");
            me.printStackTrace();
        }
    }

    void fetchHeaders(StoredMessage msg, BodyPartRequest req)
               throws MailboxException
    {
        // Iterator headerNames = req.getPartIterator();
        try
        {
            String header = msg.getMessage().getHeader();
            getConn().print("{" + header.length() + "}\r\n");
            getConn().print(header);
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