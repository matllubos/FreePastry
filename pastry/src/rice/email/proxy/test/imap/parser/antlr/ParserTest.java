package rice.email.proxy.test.imap.parser.antlr;

import antlr.Token;

import rice.email.proxy.imap.parser.antlr.*;
import rice.email.proxy.imap.commands.*;
import rice.email.proxy.imap.commands.fetch.*;

import java.io.StringReader;
import java.util.*;

import junit.framework.TestCase;


public class ParserTest extends TestCase {
  
    public ParserTest(String s){
        super(s);
    }

    AbstractImapCommand cmd;
    ImapLineParser lineParser;

    protected void setUp() throws Exception {
        lineParser = new ImapLineParser();
    }

    protected void tearDown() throws Exception {
        cmd = null;
        lineParser = null;
    }

    public void testSimpleTokenize() throws Exception {
        ImapCommandLexer lexer = new ImapCommandLexer(new StringReader("tag NOOP"));
        Token t = lexer.nextToken();
        assertEquals(lexer.ATOM, t.getType());
        assertEquals("tag", t.getText());
        t = lexer.nextToken();
        assertEquals(lexer.SPACE, t.getType());
        t = lexer.nextToken();
        assertEquals(lexer.NOOP, t.getType());
        t = lexer.nextToken();
        assertEquals(lexer.EOF, t.getType());
    }

    public void testLexerErrorRecovery() throws Exception {
        cmd = lineParser.parseCommand( "tag LIST \"\" \"No end quote...");
        assertNotNull("Command was null", cmd);
        assertTrue("Command was of type " + 
                   cmd.getClass().getName(), 
                   cmd instanceof BadSyntaxCommand);
        assertEquals(null, cmd.getTag());
    }

    public void testStringParsing() throws Exception {
        cmd = lineParser.parseCommand("tag LOGIN \"name\" \"pass\"");
        assertNotNull("Command was null", cmd);
        assertTrue("Command was of type " + 
                   cmd.getClass().getName(), 
                   cmd instanceof LoginCommand);
        LoginCommand lc = (LoginCommand) cmd;
        assertEquals("name", lc.getUser());
        assertEquals("pass", lc.getPassword());
    }

    public void testFetchParsing() throws Exception {
        cmd = lineParser.parseCommand("tag UID FETCH 1:* (RFC822.HEADER RFC822.SIZE UID)");
        assertNotNull("Command was null", cmd);
        assertTrue("Command was of type " + 
                   cmd.getClass().getName(), 
                   cmd instanceof FetchCommand);
        FetchCommand fc = (FetchCommand) cmd;
        assertEquals(3, fc.getParts().size());
        Object[] parts = fc.getParts().toArray(new Object[0]);
        assertTrue("Command was not a RFC822PartRequest", parts[0] instanceof RFC822PartRequest);
        assertTrue("Command was not a RFC822PartRequest", parts[1] instanceof RFC822PartRequest);
        assertEquals("UID", parts[2]);

        assertTrue("RFC822Part had correct subpart", ((RFC822PartRequest) parts[0]).getType().equals("HEADER"));
        assertTrue("RFC822Part had correct subpart", ((RFC822PartRequest) parts[1]).getType().equals("SIZE"));
    }

    public void testFetchBodyParsing() throws Exception {
      cmd = lineParser.parseCommand("tag UID FETCH 1:* (UID BODY)");
      assertNotNull("Command was null", cmd);
      assertTrue("Command was of type " +
                 cmd.getClass().getName(),
                 cmd instanceof FetchCommand);
      FetchCommand fc = (FetchCommand) cmd;
      assertEquals(2, fc.getParts().size());
      Object[] parts = fc.getParts().toArray(new Object[0]);
      assertEquals("UID", parts[0]);
      assertEquals("BODY", parts[1]);
    }

    public void testFetchAdvancedBodyParsing() throws Exception {
      cmd = lineParser.parseCommand("tag UID FETCH 1:* (UID BODY[1.2.HEADER.FIELDS (FROM TO SUBJECT)]<92.300>)");
      assertNotNull("Command was null", cmd);
      assertTrue("Command was of type " +
                 cmd.getClass().getName(),
                 cmd instanceof FetchCommand);
      FetchCommand fc = (FetchCommand) cmd;
      assertEquals(2, fc.getParts().size());
      Object[] parts = fc.getParts().toArray(new Object[0]);
      assertEquals("UID", parts[0]);
      assertTrue("Command was not BodyPartRequest", parts[1] instanceof BodyPartRequest);

      List list = ((BodyPartRequest) parts[1]).getType();
      assertEquals(3, list.size());
      assertEquals(list.get(0), "1");
      assertEquals(list.get(1), "2");
      assertEquals(list.get(2), "HEADER.FIELDS");

      Iterator i = ((BodyPartRequest) parts[1]).getPartIterator();
      assertTrue("Wrong number of parts", i.hasNext());
      assertEquals("FROM", i.next());
      assertTrue("Wrong number of parts", i.hasNext());
      assertEquals("TO", i.next());
      assertTrue("Wrong number of parts", i.hasNext());
      assertEquals("SUBJECT", i.next());

      assertTrue("Request has range", ((BodyPartRequest) parts[1]).hasRange());
      assertEquals(((BodyPartRequest) parts[1]).getRangeStart(), 92);
      assertEquals(((BodyPartRequest) parts[1]).getRangeLength(), 300);
    }    

    public void testAppendParsing() throws Exception {
        cmd = lineParser.parseCommand(
                      "tag APPEND \"Sent Items\" (\\Flag \\Two) \"Date\" {7}");
        assertNotNull("Command was null", cmd);
        assertTrue("Command was of type " + 
                   cmd.getClass().getName(), 
                   cmd instanceof AppendCommand);
        AppendCommand ac = (AppendCommand) cmd;
        assertEquals("Sent Items", ac.getFolder());
    }

    public void testCopyParsing() throws Exception {
        cmd = lineParser.parseCommand("tag UID COPY 15 Folder");
        assertNotNull("Command was null", cmd);
        assertTrue("Command was of type " + 
                   cmd.getClass().getName(), 
                   cmd instanceof CopyCommand);
        CopyCommand cc = (CopyCommand) cmd;
        assertEquals("Folder", cc.getFolder());
        assertNotNull(cc.getRange());
    }

    public void testStoreParsing() throws Exception {
        cmd = lineParser.parseCommand("tag UID STORE 3:6 +FLAGS.SILENT (\\Deleted)");
        assertNotNull("Command was null", cmd);
        assertTrue("Command was of type " + 
                   cmd.getClass().getName(), 
                   cmd instanceof StoreCommand);
        StoreCommand sc = (StoreCommand) cmd;
        assertEquals("+FLAGS.SILENT", sc.getType());
        assertNotNull(sc.getRange());
        assertEquals(1, sc.getFlags().size());
    }

    public void testParserErrorRecovery() throws Exception {
        cmd = lineParser.parseCommand("0002 SEELEKT \"INBOX\"");
        assertNotNull("Command was null", cmd);
        assertTrue("Command was of type " + 
                   cmd.getClass().getName(), 
                   cmd instanceof BadSyntaxCommand);
        assertEquals("0002", cmd.getTag());
    }

    public void testParserAndLexerErrorRecovery() throws Exception {
        cmd = lineParser.parseCommand("0002 SEELEKT \"INBOX");
        assertNotNull("Command was null", cmd);
        assertTrue("Command was of type " + 
                   cmd.getClass().getName(), 
                   cmd instanceof BadSyntaxCommand);
        assertEquals(null, cmd.getTag());

    }

    public void testCaseInsensitive() throws Exception {
        cmd = lineParser.parseCommand("tag NoOp");
        assertNotNull("Command was null", cmd);
        assertTrue("Command was of type " + 
                   cmd.getClass().getName(), 
                   cmd instanceof NoopCommand);

        cmd = lineParser.parseCommand("tag noop");
        assertNotNull("Command was null", cmd);
        assertTrue("Command was of type " + 
                   cmd.getClass().getName(), 
                   cmd instanceof NoopCommand);
    }
}