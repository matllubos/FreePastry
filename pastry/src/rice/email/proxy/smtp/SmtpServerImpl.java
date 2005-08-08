package rice.email.proxy.smtp;

import rice.post.*;

import rice.email.*;
import rice.email.proxy.smtp.manager.*;
import rice.email.proxy.util.*;
import rice.email.proxy.user.*;
import rice.email.proxy.smtp.commands.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;

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
  boolean authenticate = false;
  int port;
  ServerSocket server;

  SmtpManager manager;

  SmtpCommandRegistry registry;

  Workspace workspace;

  EmailService email;
  
  UserManager userManager;

  Environment environment;
  
  InetAddress localHost;
  
  public SmtpServerImpl(InetAddress localHost, int port, EmailService email, boolean gateway, PostEntityAddress address, boolean acceptNonLocal, boolean authenticate, UserManager userManager, String server, Environment env) throws Exception {
    super("SMTP Server Thread");
    this.localHost = localHost;
    this.environment = env;
    this.acceptNonLocal = acceptNonLocal;
    this.gateway = gateway;
    this.port = port;
    this.authenticate = authenticate;
    this.email = email;
    this.userManager = userManager;
    this.manager = new SimpleManager(email, gateway, address, server, env);
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

  public void initialize() throws IOException {
    server = new ServerSocket(port);
  }

  public void run() {
    try {
      while (! quit) {
        final Socket socket = server.accept();
        connections++;

        environment.getLogManager().getLogger(NonBlockingSmtpServerImpl.class, null).log(Logger.INFO,
            "Accepted connection from " + socket.getInetAddress());

        if (acceptNonLocal || gateway || socket.getInetAddress().isLoopbackAddress() ||
            (socket.getInetAddress().equals(getLocalHost()))) {
          Thread thread = new Thread("SMTP Server Thread for " + socket.getInetAddress()) {
            public void run() {
              try {
                SmtpHandler handler = new SmtpHandler(registry, manager, workspace, SmtpServerImpl.this, userManager, authenticate, environment);
                handler.handleConnection(socket);
                socket.close();
              } catch (IOException e) {
                environment.getLogManager().getLogger(NonBlockingSmtpServerImpl.class, null).logException(Logger.WARNING,
                    "IOException occurred during handling of connection - " , e);
              }
              
              connections--;
            }
          };

          thread.start();
        } else {
          environment.getLogManager().getLogger(NonBlockingSmtpServerImpl.class, null).log(Logger.WARNING,
            "Connection not local - aborting");
          
          OutputStream o = socket.getOutputStream();
          PrintWriter out = new PrintWriter(o, true);

          out.println("554 Connections only allowed locally");
          out.flush();
          socket.close();
        }
      }
    } catch (IOException e) {
      environment.getLogManager().getLogger(NonBlockingSmtpServerImpl.class, null).logException(Logger.WARNING,
          "IOException occurred during accepting of connection - " , e);
    }
  }

  public Environment getEnvironment() {
    return environment;
  }
}
