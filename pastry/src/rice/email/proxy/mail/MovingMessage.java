package rice.email.proxy.mail;

import rice.email.*;
import rice.email.proxy.util.Resource;
import rice.email.proxy.util.StreamUtils;
import rice.email.proxy.util.Workspace;
import rice.email.proxy.smtp.*;

import rice.post.*;

import java.io.*;

import java.util.*;


/**
 * Contains information for delivering a mime email.
 * 
 * <p>
 * Since a MovingMessage many be passed through many queues and
 * handlers before it can be safely deleted, destruction it handled
 * by reference counting. When an object first obtains a reference
 * to a MovingMessage, it should immediately call {@link #aquire()}.
 * As soon as it has finished processing, that object must call
 * {@link #releaseContent()}.  For example usage, see {@link
 * foedus.processing.OutgoingImpl}.
 * </p>
 */
public class MovingMessage
{
    MailAddress returnPath;
    List toAddresses = new LinkedList();
    Workspace _workspace;
    Resource _content;
    int _references = 0;
    Email email;
    PrintWriter dataWriter;

    public MovingMessage(Workspace workspace)
    {
        _workspace = workspace;
    }

    public MovingMessage(Email email)
    {
      this.email = email;
    }

    public Email getEmail() {
      return email;
    }

    public Resource getResource()
      throws IOException
    {

      return _content;
    }

    public Reader getContent()
                      throws IOException
    {

        return _content.getReader();
    }

    public void acquire()
    {
        _references++;
    }

    public void releaseContent()
    {
        if (_references > 0)
        {
            _references--;
        }
        else if (_content != null)
        {
            _workspace.release(_content);
            _content = null;
        }
    }

    public MailAddress getReturnPath()
    {

        return returnPath;
    }

    public void setReturnPath(MailAddress fromAddress)
    {
        this.returnPath = fromAddress;
    }

    public void addRecipient(MailAddress s)
    {
        toAddresses.add(s);
    }

    public void removeRecipient(MailAddress s)
    {
        toAddresses.remove(s);
    }

    public Iterator getRecipientIterator()
    {

        return toAddresses.iterator();
    }

    public void readFullContent(Reader in)
                         throws IOException
    {
        _content = _workspace.getTmpFile();
        Writer out = _content.getWriter();
        StreamUtils.copy(in, out);
        out.close();
    }

    /**
     * Reads the contents of the stream until
     * &lt;CRLF&gt;.&lt;CRLF&gt; is encountered.
     * 
     * <p>
     * It would be possible and prehaps desirable to prevent the
     * adding of an unnecessary CRLF at the end of the message, but
     * it hardly seems worth 30 seconds of effort.
     * </p>
     */
    public void readDotTerminatedContent(SmtpConnection conn) throws IOException {
      _content = _workspace.getTmpFile();
      Writer data = _content.getWriter();
      dataWriter = new PrintWriter(data);
      
      while (true) {
        String line = conn.readLine();
        if (line == null) {
          System.out.println("Did not receive <CRLF>.<CRLF> - Accepting anyway...");
          dataWriter.close();
          break;
        }
        
        if (".".equals(line.trim())) {
          dataWriter.close();
          break;
        } else {
          dataWriter.println(line);
        }
      }
    }
    
    public boolean readDotTerminatedContent(String line) throws IOException {
      if (_content == null) {
        _content = _workspace.getTmpFile();
        dataWriter = new PrintWriter(_content.getWriter());
      }
      
      if (line == null) {
        System.out.println("Did not receive <CRLF>.<CRLF> - Accepting anyway...");
        dataWriter.close();
        return true;
      }
        
      if (".\r\n".equals(line) || line.endsWith("\r\n.\r\n")) {
        dataWriter.print(line.substring(0, line.length() - 3));
        
        dataWriter.close();
        return true;
      } else {
        dataWriter.print(line);
        return false;
      }
    }
}