package rice.email.proxy.test.imap.commands;

import rice.email.proxy.imap.commands.*;
import rice.email.proxy.mail.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.util.*;

import java.io.*;

public class FetchCommandTest
    extends AbstractCommandTest
{
    FetchCommand cmd;
    Workspace workspace = new InMemoryWorkspace();

    public FetchCommandTest(String s)
    {
        super(s);
    }

    protected void setUp()
                  throws Exception
    {
        cmd = new FetchCommand();
        super.setUp(cmd);
        skipAuthentication(cmd);
    }

    protected void tearDown()
                     throws Exception
    {
        super.tearDown();
        cmd = null;
    }

    public void testHeader()
                    throws Exception
    {
        MovingMessage msg = new MovingMessage(workspace);
        msg.readFullContent(new StringReader(
                                    "Header: value\r\n" + "\r\nBody"));
        getDefaultMailbox().put(msg);
        getState().enterFolder("INBOX");

        /*
        cmd.setTag("tag");

        cmd.setRange(new MsgSetFilter("1", false));
        cmd.appendPartRequest("RFC822.HEADER");
        execute();

        expectLines(
                "* 1 FETCH (RFC822.HEADER {17}\r\n" + 
                "Header: value\r\n" + "\r\n)");

        String line = getConn().getResponse();
        assertTrue("Malformed success response: " + line, 
                   line.startsWith("tag OK FETCH")); */

        getConn().pushln("TAG FETCH 1 RFC822.HEADER");
        getConn().getResponse();
    }

    private void expectLines(String lines)
                      throws Exception
    {
        BufferedReader in = new BufferedReader(new StringReader(lines));
        String line;
        while ((line = in.readLine()) != null)
        {
            assertEquals(line, getConn().getResponse());
        }
    }

    private void execute()
                  throws IOException
    {
        cmd.execute();
        getConn().close();
    }
}