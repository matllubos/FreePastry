package rice.email.proxy.test.imap.commands;

import antlr.*;

import rice.email.proxy.imap.commands.*;
import rice.email.proxy.imap.commands.fetch.*;
import rice.email.proxy.imap.parser.antlr.*;
import rice.email.proxy.mail.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.test.mailbox.*;
import rice.email.proxy.util.*;

import java.io.*;
import java.util.*;

public class FetchCommandRegressionTest
extends AbstractCommandTest
{
  private String messageDir = "messages";
  public static final String MAGIC = ">>>>>>>>>>----------<<<<<<<<<<\n";
  
  AbstractImapCommand cmd;
  Workspace workspace = new InMemoryWorkspace();

  public FetchCommandRegressionTest(String s) {
    super(s);
  }

  protected void tearDown() throws Exception {
    super.tearDown();
    cmd = null;
  }

  public void testHeader()  throws Exception  {
    File directory = new File(messageDir);

    assertTrue(directory.isDirectory());

    File[] messages = directory.listFiles(new FileFilter() {
      public boolean accept(File pathname) {
        return pathname.getName().endsWith(".msg");
      }
    });

    for (int i=0; i<messages.length; i++) {
      System.out.println("Parsing message: '" + messages[i] + "'");
      Reader reader = (new FileResource(messages[i])).getReader();
      StringWriter writer = new StringWriter();

      StreamUtils.copy(reader, writer);

      String[] stuff = writer.toString().split(MAGIC);

      MovingMessage msg = new MovingMessage(workspace);
      msg.readFullContent(new StringReader(stuff[0]));

      for (int j=1; j<stuff.length; j+=2) {
        System.out.println("Parsing command: '" + stuff[j].trim() + "'");

        ImapLineParser parser = new ImapLineParser();
        cmd = parser.parseCommand(stuff[j].trim());
        setUp();
        setUp(cmd);
        getDefaultMailbox().put(msg);
        List list = getDefaultMailbox().getFolder("INBOX").getMessages(MsgFilter.ALL);
        MockFlagList flags = (MockFlagList) ((MockMail) list.get(0)).getFlagList();
        flags.setFlag("\\Seen", true);
        
        getState().enterFolder("INBOX");
        execute();

        expectLines(stuff[j+1].trim());
      }
    }
  }

  private synchronized void expectLines(String lines) throws Exception {
    BufferedReader in = new BufferedReader(new StringReader(lines));
    String line;
    while ((line = in.readLine()) != null) {
      String response = getConn().getResponse();

      assertTrue(response != null);
      
      assertEquals(line, response);
    }
  }
  
  private void execute() throws Exception {
    Thread t = new Thread() {
      public void run() {
        try {
          cmd.execute();
          getConn().close();
        } catch (Exception e) {
          System.out.println("ERROR IN EXECUTE " + e);
          e.printStackTrace();
        }
      }
    };

    t.start();
  }
}