package rice.email.proxy.test.smtp.commands;

import rice.email.proxy.mail.*;
import rice.email.proxy.smtp.*;
import rice.email.proxy.smtp.commands.*;
import rice.email.proxy.test.smtp.*;

import rice.email.proxy.util.*;

import java.io.*;

import junit.framework.TestCase;


public class DataCommandTest
    extends TestCase
{
    public DataCommandTest(String s)
    {
        super(s);
    }

    DataCommand cmd;
    MockSmtpConnection conn;
    SmtpState smtpState;
    MockSmtpManager manager;
    MailAddress localAddress1;
    MailAddress localAddress2;

    protected void setUp()
                  throws Exception
    {
        cmd = new DataCommand();
        conn = new MockSmtpConnection();
        smtpState = new SmtpState(new InMemoryWorkspace());
        manager = new MockSmtpManager();

        localAddress1 = new MailAddress("person1@localhost");
        localAddress2 = new MailAddress("person2@localhost");
    }

    protected void tearDown()
                     throws Exception
    {
        cmd = null;
        conn = null;
    }

    public void testNoSender()
                      throws IOException
    {
        cmd.execute(conn, smtpState, manager, "DATA");

        String response = conn.getResponse();
        assertTrue("Wrong response to out of order commands: " + 
                   response, response.startsWith("503 "));
    }

    public void testNoRecipients()
                          throws IOException
    {
        smtpState.getMessage().setReturnPath(localAddress1);

        cmd.execute(conn, smtpState, manager, "DATA");

        String response = conn.getResponse();
        assertTrue("Wrong response to out of order commands:" + 
                   response, response.startsWith("503 "));
    }

    public void testLegal()
                   throws IOException
    {
        smtpState.getMessage().setReturnPath(localAddress1);
        smtpState.getMessage().addRecipient(localAddress2);

        sendMessage();

        String response = conn.getResponse();

        assertTrue("Wouldn't take content: " + response, 
                   response.startsWith("354 "));

        response = conn.getResponse();

        assertTrue("Didn't accept a valid message: " + response, 
                   response.startsWith("250 "));

        assertNotNull("Didn't actually send an accepted message", 
                      manager.getSent());
    }

    void sendMessage()
              throws IOException
    {
        conn.pushln("Subject: subject");
        conn.pushln("");
        conn.pushln("body");
        conn.pushln(".");

        cmd.execute(conn, smtpState, manager, "DATA");
    }

    public void testJMSProblem()
                        throws IOException
    {
        manager.queueException();

        smtpState.getMessage().setReturnPath(localAddress1);
        smtpState.getMessage().addRecipient(localAddress2);

        sendMessage();

        // ignore "354 Continue"
        conn.getResponse();

        String response = conn.getResponse();

        assertTrue("Wrong internal error response: " + response, 
                   response.startsWith("451 "));
    }
}