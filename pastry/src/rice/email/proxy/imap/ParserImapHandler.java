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
package rice.email.proxy.imap;

import antlr.TokenStreamException;
import antlr.TokenStreamIOException;

import rice.email.proxy.imap.commands.AbstractImapCommand;
import rice.email.proxy.imap.commands.IllegalStateCommand;

import rice.email.proxy.imap.parser.antlr.ImapLineParser;

import rice.email.proxy.user.UserManager;

import rice.email.proxy.util.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;

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

    InetAddress localHost;
    
    protected Logger logger;
    
    public ParserImapHandler(InetAddress localHost, UserManager manager, Workspace workspace, Environment env) {
      this.localHost = localHost;
      state = new ImapState(manager, workspace, env);        
      logger = env.getLogManager().getLogger(ParserImapHandler.class, null);
    }

    public InetAddress getLocalHost() {
      return localHost; 
    }
    
    public void handleConnection(final Socket socket, Environment env) throws IOException {
        conn = new ImapConnection(this, socket, env);
        cmdParser = new ImapLineParser(env);

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
          if (logger.level <= Logger.WARNING) logger.logException(
              "",e);
        } finally {
            try {
                conn.close();
            } catch (final IOException ioe) {
              if (logger.level <= Logger.WARNING) logger.logException(
                  "PANIC: Got error " + ioe + " while closing connection!", ioe);
            }
        }

        state.cleanup();
    }

    protected void sendGreetings() {
      SimpleDateFormat df = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z (z)");
      
//s      try {
        conn.println("* OK [CAPABILITY IMAP4REV1 AUTH=CRAM-MD5] " + getLocalHost().getHostName() + " IMAP4rev1 2001.315 at " + df.format(new Date()));
//      } catch (UnknownHostException e) {
//        conn.println("* OK [CAPABILITY IMAP4REV1 AUTH=CRAM-MD5] IMAP4rev1 2001.315 at " + df.format(new Date()));
//      } 
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
          if (logger.level <= Logger.WARNING) logger.logException(
              " NO internal error ", re);
        }
    }

    public void quit() {
        quitting = true;
    }
}
