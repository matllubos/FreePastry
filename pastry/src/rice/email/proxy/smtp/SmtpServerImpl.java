package rice.email.proxy.smtp;

import rice.post.*;

import rice.email.*;
import rice.email.proxy.smtp.manager.*;
import rice.email.proxy.util.*;
import rice.email.proxy.smtp.commands.*;

import java.io.*;
import java.net.*;

public class SmtpServerImpl extends Thread implements SmtpServer {

  private boolean acceptNonLocal = false;
  private boolean proxy = false;
  private boolean quit = false;
  private int port;
  private ServerSocket server;

  private SmtpManager manager;

  private SmtpCommandRegistry registry;

  private Workspace workspace;

  private EmailService email;

  public SmtpServerImpl(int port, EmailService email, boolean proxy, PostEntityAddress address, boolean acceptNonLocal) throws Exception {
    this.acceptNonLocal = acceptNonLocal;
    this.proxy = proxy;
    this.port = port;
    this.email = email;
    this.manager = new SimpleManager(email, proxy, address);
    this.registry = new SmtpCommandRegistry();
    this.workspace = new InMemoryWorkspace();

    initialize();
  }

  public int getPort() {
    return port;
  }

  public void initialize() throws IOException {
    server = new ServerSocket(port);

    registry.load();
  }

  public void run() {
    try {
      while (! quit) {
        final Socket socket = server.accept();

        System.out.println("Accepted connection from " + socket.getInetAddress());

        if (acceptNonLocal || proxy || (socket.getInetAddress().isSiteLocalAddress())) {
          Thread thread = new Thread() {
            public void run() {
              try {
                SmtpHandler handler = new SmtpHandler(registry, manager, workspace);
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
