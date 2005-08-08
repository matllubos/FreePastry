package rice.email.proxy.pop3;

import rice.email.*;
import rice.email.proxy.pop3.commands.*;
import rice.email.proxy.user.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;

import java.io.*;
import java.net.*;

public class Pop3ServerImpl extends Thread implements Pop3Server {
  
  // networking stuff
  int port;
  ServerSocket server;
  
  // protocol stuff
  Pop3CommandRegistry registry;
  UserManager manager;
  
  boolean acceptNonLocal = false;

  Environment environment;
  
  InetAddress localHost;

  public Pop3ServerImpl(InetAddress localHost, int port, EmailService email, UserManager manager, boolean gateway, boolean acceptNonLocal, Environment env) throws IOException {
    super("POP3 Server Thread");
    this.localHost = localHost;
    this.environment = env;
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
  
  public void initialize() throws IOException {
    server = new ServerSocket(port);
  }
  
  public void run() {
    try {
      while (true) {
        final Socket socket = server.accept();
        
        log(Logger.INFO, "Accepted connection from " + socket.getInetAddress());
        
        if (acceptNonLocal || socket.getInetAddress().isLoopbackAddress() ||
            (socket.getInetAddress().equals(getLocalHost()))) {
          Thread thread = new Thread("POP3 Server Thread for " + socket.getInetAddress()) {
            public void run() {
              try {
                Pop3Handler handler = new Pop3Handler(getLocalHost(), registry, manager, environment);
                handler.handleConnection(socket);
              } catch (IOException e) {
                logException(Logger.WARNING, "IOException occurred during handling of connection - " , e);
              }
            }
          };
          
          thread.start();
        } else {
          log(Logger.WARNING, "Connection not local - aborting");
          
          OutputStream o = socket.getOutputStream();
          PrintWriter out = new PrintWriter(o, true);
          
          out.println("-ERR Connections only allowed locally");
          out.flush();
          socket.close();
        }
      }
    } catch (IOException e) {
      logException(Logger.WARNING, "IOException occurred during accepting of connection - " , e);
    }
  }
  
  private void log(int level, String message) {
    environment.getLogManager().getLogger(Pop3ServerImpl.class, null).log(level, message);    
  }
  
  private void logException(int level, String message, Throwable t) {
    environment.getLogManager().getLogger(Pop3ServerImpl.class, null).logException(level, message, t);
  }
  
  
}
