package rice.email.proxy.imap;

import rice.email.*;
import rice.email.proxy.user.*;
import rice.email.proxy.util.*;
import rice.email.proxy.mailbox.postbox.*;

import java.io.*;

import java.net.*;

public final class ImapServerImpl extends Thread implements ImapServer {

  private boolean quit = false;
  private int port;
  private ServerSocket server;

  private UserManager manager;

  private Workspace workspace;

  private EmailService email;

  public ImapServerImpl(int port, EmailService email, UserManager manager) throws IOException {
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

        System.out.println("Accepted connection from " + socket.getInetAddress());

        if (socket.getInetAddress().isSiteLocalAddress()) {
          Thread thread = new Thread() {
            public void run() {
              try {
                ParserImapHandler handler = new ParserImapHandler(manager, workspace);
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