package rice.email.proxy.mail;

import rice.email.proxy.util.*;

import java.io.*;

import java.text.*;

import java.util.*;

import javax.mail.internet.MimeBodyPart;
import javax.mail.Header;
import javax.mail.Session;
import javax.mail.MessagingException;


public class MimeMessage {

  Resource _resource;

  javax.mail.internet.MimeMessage _message;

  public static final SimpleDateFormat dateReader = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");
  public static final SimpleDateFormat dateWriter = new SimpleDateFormat("d-MMM-yyyy HH:mm:ss Z");

  public MimeMessage(Resource file) throws MailException {
    _resource = file;
    parseMessage();
  }

  public MimeMessage(javax.mail.internet.MimeMessage message) throws MailException {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      message.writeTo(baos);
      
      _resource = new StringBufferResource(baos.toString());
      _message = new javax.mail.internet.MimeMessage(message);//parseMessage();
    } catch (IOException e) {
      throw new MailException(e);
    } catch (MessagingException e) {
      throw new MailException(e);
    }
  }

  private void parseMessage() throws MailException {
    try {

      if (_message == null) {
        InputStream in = _resource.getInputStream();
        Session session = Session.getDefaultInstance(new Properties(), null);
        _message = new javax.mail.internet.MimeMessage(session, in);
        _message.getSize();
        in.close();
      }
    } catch (Exception me) {
      throw new MailException(me);
    }
  }

  public Reader getContents() throws MailException {
    try {
      return _resource.getReader();
    } catch (IOException ioe) {
      throw new MailException(ioe);
    }
  }

  public javax.mail.internet.MimeMessage getMessage() {
    return _message;
  }

  public Object getContent() throws MailException {
    try {
      return _message.getContent();
    } catch (MessagingException me) {
      throw new MailException(me);
    } catch (IOException ioe) {
      throw new MailException(ioe);
    }
  }


  public void writeTo(OutputStream out) throws MailException, IOException {
    try {
      _message.writeTo(out);
    } catch (MessagingException me) {
      throw new MailException(me);
    }
  }

  public String getHeader() throws MailException {
    StringBuffer out = new StringBuffer();
    try {
      BufferedReader in = new BufferedReader(_resource.getReader());
      String line;
      while (true) {
        line = in.readLine();
        if (line == null || "".equals(line))
          break;

        out.append(line);
        out.append("\r\n");
      }

      in.close();
    } catch (IOException ioe) {
      throw new MailException(ioe);
    }

    out.append("\r");

    return out.toString();
  }

  public String[] getHeader(String name) throws MailException {
    try {
      String[] headers =  _message.getHeader(name);

      if (headers == null)
        return new String[0];
      else
        return headers;
    } catch (MessagingException me) {
      throw new MailException(me);
    }
  }

  public Enumeration getMatchingHeaderLines(String[] names) throws MailException {
    try {
      return _message.getMatchingHeaderLines(names);
    } catch (MessagingException me) {
      throw new MailException(me);
    }
  }

  public Enumeration getNonMatchingHeaderLines(String[] names) throws MailException {
    try {
      return _message.getNonMatchingHeaderLines(names);
    } catch (MessagingException me) {
      throw new MailException(me);
    }
  }

  public long getSize() throws MailException {
    try {
      return _resource.getSize();
    } catch (IOException ioe) {
      throw new MailException(ioe);
    }
  }

  public String getInternalDate() {
    try {
      Date date = _message.getReceivedDate();

      if (date == null)
        date = _message.getSentDate();

      if (date == null)
        return "null";
      
      if (date.getDate() < 10)
        return " " + dateWriter.format(date);
      else
        return dateWriter.format(date);
    } catch (MessagingException e) {
    }

    return null;
  }
}
