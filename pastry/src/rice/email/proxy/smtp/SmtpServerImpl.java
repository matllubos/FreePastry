package rice.email.proxy.smtp;

import rice.post.*;

import rice.email.*;
import rice.email.proxy.smtp.manager.*;
import rice.email.proxy.util.*;
import rice.email.proxy.smtp.commands.*;

import java.io.*;
import java.net.*;

public class SmtpServerImpl extends Thread implements SmtpServer {
  
  public int connections = 0;
  public int success = 0;
  public int fail = 0;
  
  public int getConnections() { return connections; }
  public int getSuccess() { int result = success; success = 0; return result; }
  public int getFail() { int result = fail; fail = 0; return result; }
  public void incrementSuccess() { success++; }
  public void incrementFail() { fail++; }

  boolean acceptNonLocal = false;
  boolean gateway = false;
  boolean quit = false;
  int port;
  ServerSocket server;

  SmtpManager manager;

  SmtpCommandRegistry registry;

  Workspace workspace;

  EmailService email;

  public SmtpServerImpl(int port, EmailService email, boolean gateway, PostEntityAddress address, boolean acceptNonLocal) throws Exception {
    super("SMTP Server Thread");
    this.acceptNonLocal = acceptNonLocal;
    this.gateway = gateway;
    this.port = port;
    this.email = email;
    this.manager = new SimpleManager(email, gateway, address);
    this.registry = new SmtpCommandRegistry();
    this.registry.load();
    this.workspace = new InMemoryWorkspace();

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
      while (! quit) {
        final Socket socket = server.accept();
        connections++;

        System.out.println("Accepted connection from " + socket.getInetAddress());

        if (acceptNonLocal || gateway || socket.getInetAddress().isLoopbackAddress() ||
            (socket.getInetAddress().equals(InetAddress.getLocalHost()))) {
          Thread thread = new Thread("SMTP Server Thread for " + socket.getInetAddress()) {
            public void run() {
              try {
                SmtpHandler handler = new SmtpHandler(registry, manager, workspace, SmtpServerImpl.this);
                handler.handleConnection(socket);
                socket.close();
              } catch (IOException e) {
                System.out.println("IOException occurred during handling of connection - " + e);
              }
              
              connections--;
            }
          };

          thread.start();
        } else {
          System.out.println("Connection not local - aborting");
          
          OutputStream o = socket.getOutputStream();
          PrintWriter out = new PrintWriter(o, true);

          out.println("554 Connections only allowed locally");
          out.flush();
          socket.close();
        }
      }
    } catch (IOException e) {
       System.out.println("IOException occurred during accepting of connection - " + e);
    }
  }
}
