package rice.email.proxy.pop3;

import rice.email.*;
import rice.email.proxy.pop3.commands.*;
import rice.email.proxy.user.*;

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
  
  public Pop3ServerImpl(int port, EmailService email, UserManager manager, boolean gateway, boolean acceptNonLocal) throws IOException {
    super("POP3 Server Thread");
    this.acceptNonLocal = acceptNonLocal;
    this.port = port;
    this.manager = manager;
    this.registry = new Pop3CommandRegistry();
    this.registry.load();
    
    initialize();
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
        
        System.out.println("Accepted connection from " + socket.getInetAddress());
        
        if (acceptNonLocal || socket.getInetAddress().isLoopbackAddress() ||
            (socket.getInetAddress().equals(InetAddress.getLocalHost()))) {
          Thread thread = new Thread("POP3 Server Thread for " + socket.getInetAddress()) {
            public void run() {
              try {
                Pop3Handler handler = new Pop3Handler(registry, manager);
                handler.handleConnection(socket);
              } catch (IOException e) {
                System.out.println("IOException occurred during handling of connection - " + e);
              }
            }
          };
          
          thread.start();
        } else {
          System.out.println("Connection not local - aborting");
          
          OutputStream o = socket.getOutputStream();
          PrintWriter out = new PrintWriter(o, true);
          
          out.println("-ERR Connections only allowed locally");
          out.flush();
          socket.close();
        }
      }
    } catch (IOException e) {
      System.out.println("IOException occurred during accepting of connection - " + e);
    }
  }
}