package rice.email.proxy.test.imap.commands;

import java.io.*;

import rice.email.proxy.imap.commands.*;

public class ExamineCommandTest extends AbstractCommandTest {
    ExamineCommand cmd;

    public ExamineCommandTest(String s) {
        super(s);
    }

    protected void setUp() throws Exception {
      cmd = new ExamineCommand();
      super.setUp();
      super.setUp(cmd);
      skipAuthentication(cmd);
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        cmd = null;
    }

    public void testEmtpFold() throws Exception {
        cmd.setTag("tag");
        cmd.setFolder("INBOX");
        execute();

        String line = null;

        line = getConn().getResponse();
        assertEquals("* 0 EXISTS", line);

        line = getConn().getResponse();
        assertEquals("* 0 RECENT", line);

        line = getConn().getResponse();
        assertTrue("Expected uid info: " + line,
                   line.startsWith("* OK [UIDVALIDITY"));

        line = getConn().getResponse();
        assertTrue("Expected next uid info: " + line,
                   line.startsWith("* OK [UIDNEXT"));

        line = getConn().getResponse();
        assertTrue("Expected flag info: " + line,
                   line.startsWith("* FLAGS"));

        line = getConn().getResponse();
        assertTrue("Expected permanent flag info: " + line,
                   line.startsWith("* OK [PERMANENTFLAGS ()]"));

        line = getConn().getResponse();
        assertTrue("Malformed success response: " + line, 
                   line.startsWith("tag OK [READ-ONLY] EXAMINE"));

    }

    private void execute() throws IOException {
        cmd.execute();
        getConn().close();
    }
}