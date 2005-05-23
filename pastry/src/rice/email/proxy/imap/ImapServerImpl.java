package rice.email.proxy.imap;

import rice.email.*;
import rice.email.proxy.user.*;
import rice.email.proxy.util.*;
import rice.email.proxy.mailbox.postbox.*;

import java.io.*;

import java.net.*;

public class ImapServerImpl extends Thread implements ImapServer {
  
  boolean log;
  boolean quit = false;
  int port;
  ServerSocket server;
  
  boolean gateway;
  boolean acceptNonLocal;
  
  UserManager manager;
  
  Workspace workspace;
  
  EmailService email;

  public ImapServerImpl(int port, EmailService email, UserManager manager, boolean gateway, boolean acceptNonLocal, boolean log) throws IOException {
    super("IMAP Server Thread");
    this.acceptNonLocal = acceptNonLocal;
    this.gateway = gateway;
    this.port = port;
    this.email = email;
    this.manager = manager;
    this.workspace = new InMemoryWorkspace();
    this.log = log;

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

        System.out.println("Accepted connection from " + socket.getInetAddress());

        if (acceptNonLocal || gateway || socket.getInetAddress().isLoopbackAddress() ||
            (socket.getInetAddress().equals(InetAddress.getLocalHost()))) {
          Thread thread = new Thread("IMAP Server Thread for " + socket.getInetAddress()) {
            public void run() {
              try {
                ParserImapHandler handler = new ParserImapHandler(manager, workspace);
                handler.handleConnection(socket, log);
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

          out.println("* BAD Connections only allowed locally");
          out.flush();
          socket.close();
        }
      }
    } catch (IOException e) {
      System.out.println("IOException occurred during accepting of connection - " + e);
    }
  }
}
