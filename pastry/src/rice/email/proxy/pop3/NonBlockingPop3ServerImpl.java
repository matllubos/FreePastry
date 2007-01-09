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
package rice.email.proxy.pop3;

import rice.email.*;
import rice.email.proxy.pop3.commands.*;
import rice.email.proxy.user.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.selector.*;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;

public class NonBlockingPop3ServerImpl extends SelectionKeyHandler implements Pop3Server {
  
  // networking stuff
  int port;
  
  // protocol stuff
  Pop3CommandRegistry registry;
  UserManager manager;
  
  boolean acceptNonLocal = false;
  
  Environment environment;
  
  InetAddress localHost;
  
  protected Logger logger;

  public NonBlockingPop3ServerImpl(InetAddress localHost, int port, EmailService email, UserManager manager, boolean gateway, boolean acceptNonLocal, Environment env) throws IOException {
    this.localHost = localHost;
    this.environment = env;
    this.logger = environment.getLogManager().getLogger(NonBlockingPop3ServerImpl.class, null);
    this.acceptNonLocal = acceptNonLocal;
    this.port = port;
    this.manager = manager;
    this.registry = new Pop3CommandRegistry();
    this.registry.load(environment);
    
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
    final ServerSocketChannel channel = ServerSocketChannel.open();
    channel.configureBlocking(false);
    channel.socket().bind(new InetSocketAddress(getPort()));
    
    // register interest in accepting connections
    environment.getSelectorManager().invoke(new Runnable() {
      public void run() {
        try {
          environment.getSelectorManager().register(channel, NonBlockingPop3ServerImpl.this, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
          if (logger.level <= Logger.WARNING) logger.logException("ERROR creating POP3 server socket key " , e);
        }
      }
    });
  }
  
  public void accept(SelectionKey key) {
    try {
      final Socket socket = ((SocketChannel) ((ServerSocketChannel) key.channel()).accept()).socket();
      
      if (logger.level <= Logger.INFO) logger.log("Accepted connection from " + socket.getInetAddress());
      
      if (acceptNonLocal || socket.getInetAddress().isLoopbackAddress() ||
          (socket.getInetAddress().equals(getLocalHost()))) {
        Thread thread = new Thread("POP3 Server Thread for " + socket.getInetAddress()) {
          public void run() {
            try {
              Pop3Handler handler = new Pop3Handler(getLocalHost(), registry, manager, environment);
              handler.handleConnection(socket);
            } catch (IOException e) {
              if (logger.level <= Logger.WARNING) logger.logException("IOException occurred during handling of connection - " , e);
            }
          }
        };
        
        thread.start();
      } else {
        if (logger.level <= Logger.WARNING) logger.log("Connection not local - aborting");
        
        OutputStream o = socket.getOutputStream();
        PrintWriter out = new PrintWriter(o, true);
        
        out.println("-ERR Connections only allowed locally");
        out.flush();
        socket.close();
      }      
    } catch (IOException e) {
      if (logger.level <= Logger.WARNING) logger.logException("IOException occurred during accepting of connection - " , e);
    }
  }  
}
