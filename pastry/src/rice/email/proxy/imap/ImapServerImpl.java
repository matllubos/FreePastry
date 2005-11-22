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
  
  InetAddress localHost;
  
  protected Logger logger;
  
  public ImapServerImpl(InetAddress bindAddress, int port, EmailService email, UserManager manager, boolean gateway, boolean acceptNonLocal, Environment env) throws IOException {
    super("IMAP Server Thread");
    this.localHost = bindAddress;
    this.environment = env;
    this.acceptNonLocal = acceptNonLocal;
    this.gateway = gateway;
    this.port = port;
    this.email = email;
    this.manager = manager;
    this.logger = environment.getLogManager().getLogger(getClass(), null);
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
      }
    } catch (IOException e) {
      if (logger.level <= Logger.WARNING) logger.logException(
          "IOException occurred during accepting of connection - " , e);
    }
  }  
}
