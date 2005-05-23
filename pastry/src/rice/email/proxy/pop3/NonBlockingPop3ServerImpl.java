package rice.email.proxy.pop3;

import rice.email.*;
import rice.email.proxy.pop3.commands.*;
import rice.email.proxy.user.*;
import rice.selector.*;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;

public class NonBlockingPop3ServerImpl extends SelectionKeyHandler implements Pop3Server {
  
  // networking stuff
  int port;
  
  // protocol stuff
  Pop3CommandRegistry registry;
  UserManager manager;
  boolean log;
  
  boolean acceptNonLocal = false;
  
  public NonBlockingPop3ServerImpl(int port, EmailService email, UserManager manager, boolean gateway, boolean acceptNonLocal, boolean log) throws IOException {
    this.acceptNonLocal = acceptNonLocal;
    this.port = port;
    this.manager = manager;
    this.registry = new Pop3CommandRegistry();
    this.registry.load();
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
    SelectorManager.getSelectorManager().invoke(new Runnable() {
      public void run() {
        try {
          SelectorManager.getSelectorManager().register(channel, NonBlockingPop3ServerImpl.this, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
          System.out.println("ERROR creating POP3 server socket key " + e);
        }
      }
    });
  }
  
  public void accept(SelectionKey key) {
    try {
      final Socket socket = ((SocketChannel) ((ServerSocketChannel) key.channel()).accept()).socket();
      
      System.out.println("Accepted connection from " + socket.getInetAddress());
      
      if (acceptNonLocal || socket.getInetAddress().isLoopbackAddress() ||
          (socket.getInetAddress().equals(InetAddress.getLocalHost()))) {
        Thread thread = new Thread("POP3 Server Thread for " + socket.getInetAddress()) {
          public void run() {
            try {
              Pop3Handler handler = new Pop3Handler(registry, manager);
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
        
        out.println("-ERR Connections only allowed locally");
        out.flush();
        socket.close();
      }      
    } catch (IOException e) {
      System.out.println("IOException occurred during accepting of connection - " + e);
    }
  }
}