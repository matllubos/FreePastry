package rice.email.proxy.imap;

import rice.email.*;
import rice.email.proxy.user.*;
import rice.email.proxy.util.*;
import rice.email.proxy.mailbox.postbox.*;

import java.io.IOException;

import java.net.*;

public final class ImapServerImpl extends Thread implements ImapServer {

  private boolean quit = false;
  private int port;
  private ServerSocket server;

  private UserManager manager;

  private Workspace workspace;

  private EmailService email;

  public ImapServerImpl(int port, EmailService email) throws IOException {
    this.port = port;
    this.email = email;
    this.manager = new UserManagerImpl(email, new PostMailboxManager(email));
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

        System.out.println("Accepted connection...");

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
      }
    } catch (IOException e) {
      System.out.println("IOException occurred during accepting of connection - " + e);
    }
  }
}