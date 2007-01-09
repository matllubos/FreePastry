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
package rice.email.proxy.smtp;

import rice.post.*;

import rice.email.*;
import rice.email.proxy.smtp.manager.*;
import rice.email.proxy.util.*;
import rice.email.proxy.user.*;
import rice.email.proxy.smtp.commands.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.selector.*;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;

public class NonBlockingSmtpServerImpl extends SelectionKeyHandler implements SmtpServer {

  public static int MAX_CONNECTIONS = 6;
  
  public int connections1 = 0;
  public int success = 0;
  public int fail = 0;
  
  public int getConnections() { return connections1; }
  public int getSuccess() { int result = success; success = 0; return result; }
  public int getFail() { int result = fail; fail = 0; return result; }
  public void incrementSuccess() { success++; }
  public void incrementFail() { fail++; }
  
  boolean acceptNonLocal = false;
  boolean gateway = false;
  boolean quit = false;
  boolean authenticate = false;
  int port;

  SmtpManager manager;

  SmtpCommandRegistry registry;

  Workspace workspace;

  EmailService email;
  
  ServerSocketChannel channel;
  
  SelectionKey key;
  
  UserManager userManager;
  
  int connections = 0;

  Environment environment;
  
  InetAddress localHost;
  
  protected Logger logger;
  
  public NonBlockingSmtpServerImpl(InetAddress localHost, int port, EmailService email, boolean gateway, PostEntityAddress address, boolean acceptNonLocal, boolean authenticate, UserManager userManager, String server, Environment env) throws Exception {
    this.environment = env;
    this.logger = environment.getLogManager().getLogger(NonBlockingSmtpServerImpl.class, null);
    this.localHost = localHost;
    this.acceptNonLocal = acceptNonLocal;
    this.gateway = gateway;
    this.port = port;
    this.authenticate = authenticate;
    this.email = email;
    this.userManager = userManager;
    this.manager = new SimpleManager(email, gateway, address, server, environment);
    this.registry = new SmtpCommandRegistry();
    this.registry.load();
    this.workspace = new InMemoryWorkspace();
    initialize();
  }

  public InetAddress getLocalHost() {
    return localHost;
  }
  
  public int getPort() {
    return port;
  }
  
  public void start() {
  }

  public void initialize() throws IOException {
    // bind to port
    channel = ServerSocketChannel.open();
    channel.configureBlocking(false);
    channel.socket().bind(new InetSocketAddress(getPort()));
    
    environment.getSelectorManager().invoke(new Runnable() {
      public void run() {
        try {
          key = environment.getSelectorManager().register(channel, NonBlockingSmtpServerImpl.this, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
          if (logger.level <= Logger.WARNING) logger.logException(
              "ERROR modifiying SMTP server socket key " , e);
        }
      }
    });
  }
  
  protected void setAcceptable(final boolean acceptable) {
    // register interest in accepting connections
    environment.getSelectorManager().invoke(new Runnable() {
      public void run() {
        key.interestOps((acceptable ? SelectionKey.OP_ACCEPT : 0));
      }
    });
  }
  
  public void accept(SelectionKey key) {
    try {
      boolean turnoff = false;
      synchronized (this) {
        connections++;
      
        turnoff = (connections >= MAX_CONNECTIONS);
      }
      
      if (turnoff)
        setAcceptable(false);
      
      final Socket socket = ((SocketChannel) ((ServerSocketChannel) key.channel()).accept()).socket();
      connections1++;
      
      if (logger.level <= Logger.INFO) logger.log(
          "Accepted connection " + connections + " of " + MAX_CONNECTIONS + " from " + socket.getInetAddress());
      
      if (acceptNonLocal || gateway || socket.getInetAddress().isLoopbackAddress() ||
          (socket.getInetAddress().equals(getLocalHost()))) {
        Thread thread = new Thread("SMTP Server Thread for " + socket.getInetAddress()) {
          public void run() {
            try {
              SmtpHandler handler = new SmtpHandler(registry, manager, workspace, NonBlockingSmtpServerImpl.this, userManager, authenticate, environment);
              handler.handleConnection(socket);
              
              synchronized (NonBlockingSmtpServerImpl.this) {
                connections--; 
                connections1--;
              }
              
              setAcceptable(true);
              if (logger.level <= Logger.INFO) logger.log(
                  "Done with connection - now at " + connections + " of " + MAX_CONNECTIONS);
            } catch (IOException e) {
              if (logger.level <= Logger.WARNING) logger.logException(
                  "IOException occurred during handling of connection - " , e);
            } finally {
              try {
                socket.close();
              } catch (IOException e) {
                if (logger.level <= Logger.SEVERE) logger.logException(
                    "ERROR!!!! - Got exception " + e + " while closing socket!",e);
              }
            }
          }
        };
        
        thread.start();
      } else {
        if (logger.level <= Logger.WARNING) logger.log(
          "Connection not local - aborting");
        
        OutputStream o = socket.getOutputStream();
        PrintWriter out = new PrintWriter(o, true);
        
        out.println("554 Connections only allowed locally");
        out.flush();
        socket.close();
      }
    } catch (IOException e) {
      if (logger.level <= Logger.WARNING) logger.logException(
          "IOException occurred during accepting of connection - " , e);
    }
  }

  public Environment getEnvironment() {
    return environment;
  }
}
