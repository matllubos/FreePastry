package rice.email.proxy.imap;

import rice.email.*;
import rice.email.proxy.user.*;
import rice.email.proxy.util.*;
import rice.email.proxy.mailbox.postbox.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.selector.*;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;

public class NonBlockingImapServerImpl extends SelectionKeyHandler implements ImapServer {
  
  boolean quit = false;
  int port;
  
  boolean gateway;
  boolean acceptNonLocal;
  
  UserManager manager;
  
  Workspace workspace;
  
  EmailService email;

  Environment environment;
  
  InetAddress localHost;
  
  Logger logger;
  
  public NonBlockingImapServerImpl(InetAddress bindAddress, int port, EmailService email, UserManager manager, boolean gateway, boolean acceptNonLocal, Environment env) throws IOException {
    this.environment = env;
    this.localHost = bindAddress;
    this.acceptNonLocal = acceptNonLocal;
    this.gateway = gateway;
    this.port = port;
    this.email = email;
    this.manager = manager;
    this.workspace = new InMemoryWorkspace();
    logger = environment.getLogManager().getLogger(NonBlockingImapServerImpl.class, null);

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
          environment.getSelectorManager().register(channel, NonBlockingImapServerImpl.this, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
          if (logger.level <= Logger.WARNING) logger.logException(
              "ERROR creating IMAP server socket key " , e);
        }
      }
    });
  }

  public void accept(SelectionKey key) {
    try {
      final Socket socket = ((SocketChannel) ((ServerSocketChannel) key.channel()).accept()).socket();
      
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
    } catch (IOException e) {
      if (logger.level <= Logger.WARNING) logger.logException(
          "IOException occurred during accepting of connection - " , e);
    }
  }
}
