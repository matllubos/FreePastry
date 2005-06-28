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
  
  public ImapServerImpl(int port, EmailService email, UserManager manager, boolean gateway, boolean acceptNonLocal, Environment env) throws IOException {
    super("IMAP Server Thread");
    this.environment = env;
    this.acceptNonLocal = acceptNonLocal;
    this.gateway = gateway;
    this.port = port;
    this.email = email;
    this.manager = manager;
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

        log(Logger.INFO, "Accepted connection from " + socket.getInetAddress());

        if (acceptNonLocal || gateway || socket.getInetAddress().isLoopbackAddress() ||
            (socket.getInetAddress().equals(InetAddress.getLocalHost()))) {
          Thread thread = new Thread("IMAP Server Thread for " + socket.getInetAddress()) {
            public void run() {
              try {
                ParserImapHandler handler = new ParserImapHandler(manager, workspace, environment);
                handler.handleConnection(socket, environment);
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

          out.println("* BAD Connections only allowed locally");
          out.flush();
          socket.close();
        }
      }
    } catch (IOException e) {
      logException(Logger.WARNING, "IOException occurred during accepting of connection - " , e);
    }
  }
  private void log(int level, String message) {
    environment.getLogManager().getLogger(ImapServerImpl.class, null).log(level, message);
  }
  private void logException(int level, String message, Throwable t) {
    environment.getLogManager().getLogger(ImapServerImpl.class, null).logException(level, message, t);
  }
  
  
}
