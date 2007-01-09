/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
package rice.email.proxy.imap;

import rice.email.*;
import rice.email.proxy.user.*;
import rice.email.proxy.util.*;
import rice.email.proxy.mailbox.postbox.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;

import java.io.*;

import java.net.*;

public class ImapServerImpl extends Thread implements ImapServer {
  
  boolean quit = false;
  int port;
  ServerSocket server;
  
  boolean gateway;
  boolean acceptNonLocal;
  
  UserManager manager;
  
  Workspace workspace;
  
  EmailService email;

  Environment environment;
  
  InetAddress localHost;
  
  protected Logger logger;
  
  public ImapServerImpl(InetAddress bindAddress, int port, EmailService email, UserManager manager, boolean gateway, boolean acceptNonLocal, Environment env) throws IOException {
    super("IMAP Server Thread");
    this.localHost = bindAddress;
    this.environment = env;
    this.acceptNonLocal = acceptNonLocal;
    this.gateway = gateway;
    this.port = port;
    this.email = email;
    this.manager = manager;
    this.logger = environment.getLogManager().getLogger(getClass(), null);
    this.workspace = new InMemoryWorkspace();
    initialize();
  }

  public InetAddress getLocalHost() {
    return localHost; 
  }
  
  public int getPort() {
    return port;
  }

  public void initialize() throws IOException {
    server = new ServerSocket(port);
  }

  public void run() {
    try {
      while (! quit) {
        final Socket socket = server.accept();

        if (logger.level <= Logger.INFO) logger.log(
            "Accepted connection from " + socket.getInetAddress());

        if (acceptNonLocal || gateway || socket.getInetAddress().isLoopbackAddress() ||
            (socket.getInetAddress().equals(getLocalHost()))) {
          Thread thread = new Thread("IMAP Server Thread for " + socket.getInetAddress()) {
            public void run() {
              try {
                ParserImapHandler handler = new ParserImapHandler(getLocalHost(), manager, workspace, environment);
                handler.handleConnection(socket, environment);
              } catch (IOException e) {
                if (logger.level <= Logger.WARNING) logger.logException(
                    "IOException occurred during handling of connection - " , e);
              }
            }
          };

          thread.start();
        } else {
          if (logger.level <= Logger.WARNING) logger.log(
              "Connection not local - aborting");

          OutputStream o = socket.getOutputStream();
          PrintWriter out = new PrintWriter(o, true);

          out.println("* BAD Connections only allowed locally");
          out.flush();
          socket.close();
        }
      }
    } catch (IOException e) {
      if (logger.level <= Logger.WARNING) logger.logException(
          "IOException occurred during accepting of connection - " , e);
    }
  }  
}
