/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/
package rice.email.proxy.mail;

import rice.email.*;
import rice.email.proxy.util.Resource;
import rice.email.proxy.util.StreamUtils;
import rice.email.proxy.util.Workspace;
import rice.email.proxy.smtp.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;

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
    public void readDotTerminatedContent(SmtpConnection conn, Environment env) throws IOException {
      _content = _workspace.getTmpFile();
      Writer data = _content.getWriter();
      dataWriter = new PrintWriter(data);
      
      while (true) {
        String line = conn.readLine();
        if (line == null) {          
          Logger logger = env.getLogManager().getLogger(MovingMessage.class, null);
          if (logger.level <= Logger.WARNING) logger.log( 
              "Did not receive <CRLF>.<CRLF> - Accepting anyway...");
          dataWriter.close();
          break;
        }
        
        if (".".equals(line.trim())) {
          dataWriter.close();
          break;
        } else {
          dataWriter.print(line + "\n");
        }
      }
    }
    
    public boolean readDotTerminatedContent(String line, Environment env) throws IOException {
      if (_content == null) {
        _content = _workspace.getTmpFile();
        dataWriter = new PrintWriter(_content.getWriter());
      }
      
      if (line == null) {
        Logger logger = env.getLogManager().getLogger(MovingMessage.class, null);
        if (logger.level <= Logger.WARNING) logger.log( 
            "Did not receive <CRLF>.<CRLF> - Accepting anyway...");
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
