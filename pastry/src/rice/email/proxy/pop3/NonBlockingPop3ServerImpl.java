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
