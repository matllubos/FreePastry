package rice.email.proxy.test.mailbox;

import rice.*;
import rice.Continuation.*;

import rice.email.*;
import rice.email.proxy.mail.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.mailbox.postbox.*;
import rice.email.proxy.util.*;

import java.io.*;
import java.util.*;


public class MockMailFolder
    implements MailFolder
{
    String _name;
    String _uid;
    List messages = new ArrayList();
    int _nextUID;

    public MockMailFolder(String name)
    {
        _name = name;
        _uid = "" + UIDFactory.getUniqueId();
        _nextUID = 1;
    }

    public String getFullName()
    {

        return _name;
    }

    public int getExists() {
      try {
        return getMsgCount();
      } catch (MailboxException e) {
        return 0;
      }
    }

    public int getRecent() {
      try {
        return getRecentMsgCount();
      } catch (MailboxException e) {
        return 0;
      }
    }

    public int getMsgCount()
                    throws MailboxException
    {

        return messages.size();
    }

    public int getRecentMsgCount()
                          throws MailboxException
    {
        int count = 0;
        for (Iterator i = messages.iterator(); i.hasNext();)
        {
            MockMail m = (MockMail) i.next();
            if (m.getFlagList().isRecent())
                count++;
        }

        return count;
    }

    public String getUIDValidity()
                          throws MailboxException
    {

        return _uid;
    }

    public int getNextUID() {
      return _nextUID;
    }

    public List getMessages(MsgFilter range)
                     throws MailboxException
    {
        List results = new ArrayList();
        int count = 1;
        for (Iterator i = messages.iterator(); i.hasNext();)
        {
            MockMail message = (MockMail) i.next();
            message.setSequenceNumber(count);
            if (range.includes(message))
                results.add(message);

            count++;
        }

        ;

        return results;
    }

    public void appendMessage(String content) throws MailboxException {
      MockMail mail = new MockMail(_nextUID);
      _nextUID++;
      Email email = PostMessage.parseEmail(new StringBufferResource(content));

      mail.setMessage(email);

      messages.add(mail);
    }

    public void put(MovingMessage msg)
             throws MailboxException
    {
        try
        {
            appendMessage(StreamUtils.toString(msg.getContent()));
        }
        catch (IOException e)
        {
            throw new MailboxException(e);
        }
    }

    public void put(MovingMessage msg, List flags, long date)
      throws MailboxException
    {
      try
    {
      appendMessage(StreamUtils.toString(msg.getContent()));
    }
      catch (IOException e)
    {
        throw new MailboxException(e);
    }
    }
    
    public void copy(MovingMessage[] messages, List[] flags, long[] dates) throws MailboxException {
      throw new MailboxException("COPY NOT IMPLEMENTED!");
    }
    
    public void purge(StoredMessage[] messages) throws MailboxException {
      throw new MailboxException("PURGE NOT IMPLEMENTED!");
    }
    
    public void update(StoredMessage[] messages) throws MailboxException {
      throw new MailboxException("UPDATE NOT IMPLEMENTED!");
    }    
    
    public MailFolder createChild(String name) throws MailboxException {
      throw new MailboxException("FOLDERS NOT IMPLEMENTED!");
    }
    
    public MailFolder getChild(String name) throws MailboxException {
      throw new MailboxException("FOLDERS NOT IMPLEMENTED!");
    }
    
    public MailFolder[] getChildren() throws MailboxException {
      throw new MailboxException("FOLDERS NOT IMPLEMENTED!");
    }
    
    public void delete() throws MailboxException {
      throw new MailboxException("DELTE NOT IMPLEMENTED!");
    }
}