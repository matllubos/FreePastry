package rice.email.proxy.web;

import rice.email.*;
import rice.email.proxy.user.*;
import rice.email.proxy.util.*;
import rice.email.proxy.mailbox.postbox.*;

import java.io.*;
import java.net.*;
import java.util.*;

public class WebServerImpl extends Thread implements WebServer {
  
  protected boolean quit = false;
  protected int port;
  protected ServerSocket server;
  protected UserManager manager;  
  protected Workspace workspace;  
  protected EmailService email;
  
  protected HashMap states;
  
  public WebServerImpl(int port, EmailService email, UserManager manager) throws IOException {
    super("Web Server Thread");
    this.port = port;
    this.email = email;
    this.manager = manager;
    this.workspace = new InMemoryWorkspace();
    this.states = new HashMap();
    
    initialize();
  }
  
  public int getPort() {
    return port;
  }
  
  public void initialize() throws IOException {
    server = new ServerSocket(port);
  }
  
  protected WebState getState(InetAddress address) {
    WebState result = (WebState) states.get(address);
    
    if (result == null) {
      result = new WebState(manager);
      states.put(address, result);
    }
    
    return result;
  }
  
  public void run() {
    try {
      while (! quit) {
        final Socket socket = server.accept();
        
        System.out.println("Accepted web connection from " + socket.getInetAddress());
        
        
        Thread thread = new Thread("Web Server Thread for " + socket.getInetAddress()) {
          public void run() {
            try {
              WebHandler handler = new WebHandler(manager, workspace, getState(socket.getInetAddress()));
              handler.handleConnection(socket);
            } catch (IOException e) {
              System.out.println("IOException occurred during handling of connection - " + e);
            }
          }
        };
        
        thread.start();
      }
    } catch (IOException e) {
      System.out.println("IOException occurred during accepting of connection - " + e);
    }
  }
}
