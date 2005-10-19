package rice.email.proxy.test.smtp;

import rice.email.proxy.mail.*;
import rice.email.proxy.smtp.*;
import rice.email.proxy.smtp.manager.*;


public class MockSmtpManager
    implements SmtpManager
{
    public String checkSender(SmtpState state, MailAddress sender)
    {

        return null;
    }

    public String checkRecipient(SmtpState state, MailAddress rcpt)
    {

        return null;
    }

    public String checkData(SmtpState state)
    {

        return null;
    }

    SmtpState sent;

    public void send(SmtpState state, boolean proxy)
    {
        if (throwingException)
            throw new RuntimeException("This exception is for testing purposes");

        sent = state;
    }

    boolean throwingException;

    public SmtpState getSent()
    {

        return sent;
    }

    public void queueException()
    {
        throwingException = true;
    }
}