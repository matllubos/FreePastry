package rice.email.proxy.test.mailbox;

import rice.email.proxy.mail.*;
import rice.email.proxy.mailbox.*;

public class MockMail
    implements StoredMessage
{
    MimeMessage _msg;
    FlagList _flags;
    int _sequenceNumber;
    int _uid;

    public MockMail(int uid) {
      _uid = uid;
    }

    public void setMessage(MimeMessage msg)
    {
        _msg = msg;
        _flags = new MockFlagList();
    }

    public MimeMessage getMessage()
    {

        return _msg;
    }

    public FlagList getFlagList()
    {

        return _flags;
    }

    public int getSequenceNumber()
    {

        return _sequenceNumber;
    }

    public void setSequenceNumber(int value)
    {
        _sequenceNumber = value;
    }

    public int getUID()
    {

        return _uid;
    }

    public void purge()
               
    {
    	_msg = null;
    	_flags = null;
    }
}