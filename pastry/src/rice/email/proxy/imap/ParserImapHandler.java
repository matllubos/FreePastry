package rice.email.proxy.imap;

import antlr.TokenStreamException;
import antlr.TokenStreamIOException;

import rice.email.proxy.imap.commands.AbstractImapCommand;
import rice.email.proxy.imap.commands.IllegalStateCommand;

import rice.email.proxy.imap.parser.antlr.ImapLineParser;

import rice.email.proxy.user.UserManager;

import rice.email.proxy.util.*;

import java.io.IOException;

import java.util.*;
import java.text.*;

import java.net.*;
import java.net.SocketTimeoutException;


final class ParserImapHandler implements Quittable {
  
    ImapConnection conn;
    boolean quitting;
    String currentLine;
    ImapState state;
    ImapLineParser cmdParser;

    public ParserImapHandler(UserManager manager, Workspace workspace) {
        state = new ImapState(manager, workspace);
    }

    public void handleConnection(final Socket socket) throws IOException {
        conn = new ImapConnection(this, socket);
        cmdParser = new ImapLineParser();

        try {
            quitting = false;

            sendGreetings();

            while (!quitting) 
                handleCommand();

            conn.close();
        } catch (SocketTimeoutException ste) {
            conn.println("* BYE Autologout; idle for too long");
        } catch (TokenStreamIOException tsioe) {
            conn.println("* BYE Autologout; idle for too long");
        } catch (DisconnectedException de) {
        } catch (final Exception e) {
          e.printStackTrace();
        } finally {
            try {
                conn.close();
            } catch (final IOException ioe) {
              System.out.println("PANIC: Got error " + ioe + " while closing connection!");
            }
        }

        state.cleanup();
    }

    protected void sendGreetings() {
      SimpleDateFormat df = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z (z)");
      
      try {
        conn.println("* OK [CAPABILITY IMAP4REV1 AUTH=CRAM-MD5] " + InetAddress.getLocalHost().getHostName() + " IMAP4rev1 2001.315 at " + df.format(new Date()));
      } catch (UnknownHostException e) {
        conn.println("* OK [CAPABILITY IMAP4REV1 AUTH=CRAM-MD5] IMAP4rev1 2001.315 at " + df.format(new Date()));
      } 
    }

    protected void handleCommand() throws IOException, TokenStreamException {
        String line = conn.readLine();
        AbstractImapCommand cmd = cmdParser.parseCommand(line);

        if (cmd == null)
          throw new RuntimeException("Command was null!");
        
        if (!cmd.isValidForState(state)) {
            AbstractImapCommand command = new IllegalStateCommand();
          command.setTag(cmd.getTag()); 
          cmd = command;
        }

        cmd.setConn(conn);
        cmd.setState(state);
        try {
            cmd.execute();
        } catch (RuntimeException re) {
          conn.println(cmd.getTag() + " NO internal error " + re);
          re.printStackTrace();
        }
    }

    public void quit() {
        quitting = true;
    }
}