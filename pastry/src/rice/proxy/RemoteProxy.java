package rice.proxy;

import java.io.*;
import java.net.*;
import com.mindbright.ssh2.*;
import com.mindbright.util.*;
import rice.post.security.ca.*;

public class RemoteProxy {
  
  protected String remote;
  protected int remotePort;
  protected int localPort;
  protected String username;
  protected String password;
  
  protected SSHTunnel TCPtunnel;
  protected UDPBridge UDPbridge;
  
  public RemoteProxy(String remote, String username, String password, int localPort, int remotePort) {    
    this.remote = remote;
    this.remotePort = remotePort;
    this.localPort = localPort;
    this.username = username;
    this.password = password;
    
    debug("Running proxy tunnel to " + remote + ":22 forwarding remote port " + remotePort + " to local port " + localPort);
  }
  
  public void run() {
    UDPbridge = new UDPBridge(localPort, localPort + 1, false);
    UDPbridge.start();
    
    TCPtunnel = new SSHTunnel(remote, username, password, new int[] {localPort, localPort+1}, new int[] {remotePort, remotePort+1}, "./java -classpath pastry.jar rice.proxy.UDPBridge -UDPport " + remotePort + " -TCPport " + (remotePort+1));
    TCPtunnel.start();
  }
  
  public void debug(String message) {
    System.out.println("RMT DEBUG: " + message);
  }
  
  public static void main(String[] args) throws IOException {
    boolean input = true;
    int remotePort = 30000;
    int localPort = 30000;
    
    String host = args[0];
    String username = args[1];
    String password = null;
    
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-password") && i+1 < args.length) {
        password = args[i+1];
        break;
      }
    }
    
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-remotePort") && i+1 < args.length) {
        int n = Integer.parseInt(args[i+1]);
        if (n > 0) remotePort = n;
        break;
      }
    }
    
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-localPort") && i+1 < args.length) {
        int n = Integer.parseInt(args[i+1]);
        if (n > 0) localPort = n;
        break;
      }
    }
    
    if (password == null) 
      password = CAKeyGenerator.fetchPassword("Enter your SSH password");    
    
    RemoteProxy proxy = new RemoteProxy(host, username, password, localPort, remotePort);
    proxy.run();
  }
  
}