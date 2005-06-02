package rice.email.proxy.imap;

import rice.email.*;
import rice.email.proxy.user.*;
import rice.email.proxy.util.*;
import rice.email.proxy.mailbox.postbox.*;
import rice.environment.Environment;
import rice.selector.*;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;

public class NonBlockingImapServerImpl extends SelectionKeyHandler implements ImapServer {
  
  boolean log;
  boolean quit = false;
  int port;
  
  boolean gateway;
  boolean acceptNonLocal;
  
  UserManager manager;
  
  Workspace workspace;
  
  EmailService email;

  Environment environment;
  
  public NonBlockingImapServerImpl(int port, EmailService email, UserManager manager, boolean gateway, boolean acceptNonLocal, boolean log, Environment env) throws IOException {
    this.environment = env;
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
  
  public void start() {
  }

  public void initialize() throws IOException {
    // bind to port
    final ServerSocketChannel channel = ServerSocketChannel.open();
    channel.configureBlocking(false);
    channel.socket().bind(new InetSocketAddress(getPort()));
    
    // register interest in accepting connections
    environment.getSelectorManager().invoke(new Runnable() {
      public void run() {
        try {
          environment.getSelectorManager().register(channel, NonBlockingImapServerImpl.this, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
          System.out.println("ERROR creating IMAP server socket key " + e);
        }
      }
    });
  }

  public void accept(SelectionKey key) {
    try {
      final Socket socket = ((SocketChannel) ((ServerSocketChannel) key.channel()).accept()).socket();
      
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
    } catch (IOException e) {
      System.out.println("IOException occurred during accepting of connection - " + e);
    }
  }
}
