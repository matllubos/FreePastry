package rice.proxy;

import java.io.*;
import java.net.*;
import com.mindbright.ssh2.*;
import com.mindbright.util.*;
import rice.post.security.ca.*;

public class SSHTunnel extends Thread {
  
  public static int SSH_PORT = 22;
  
  protected String remote;
  protected int[] remotePorts;
  protected int[] localPorts;
  protected String username;
  protected String password;
  protected String command;
  
  public SSHTunnel(String remote, String username, String password, int[] localPorts, int[] remotePorts, String command) {
    super("SSH Tunnel Thread");
    
    this.remote = remote;
    this.remotePorts = remotePorts;
    this.localPorts = localPorts;
    this.username = username;
    this.password = password;
    this.command = command;
    
    debug("Running SSH tunnel to " + remote + ":22 forwarding remote port " + remotePorts + " to local port " + localPorts);
  }
  
  public void run() {
    try {
      SSH2Transport transport = new SSH2Transport(new Socket(remote, SSH_PORT), new SecureRandomAndPad());
      SSH2SimpleClient client = new SSH2SimpleClient(transport, username, password);
      SSH2Connection connection = client.getConnection();
      
      for (int i=0; i<remotePorts.length; i++) {
        connection.newRemoteForwardBlocking(remote, remotePorts[i], "localhost", localPorts[i], null);
        debug("Set up port forwarding from remote " + remotePorts[i] + " to local " + localPorts[i]);
      }
        
      SSH2ConsoleRemote console = new SSH2ConsoleRemote(connection);
      
      debug("Connected, executing command " + command);
      
      console.command(command);
      BufferedReader reader = new BufferedReader(new InputStreamReader(console.getStdOut()));
      String line = null;
      
      while ((line = reader.readLine()) != null)
        debug(line);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  public void debug(String message) {
    System.out.println("SSH DEBUG: " + message);
  }
  
  public static void main(String[] args) throws IOException {
    boolean input = true;
    int remotePort = 30000;
    int localPort = 30000;
    
    String host = args[0];
    String username = args[1];
    
    String password = CAKeyGenerator.fetchPassword("Enter your SSH password");
        
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
    
    SSHTunnel tunnel = new SSHTunnel(host, username, password, new int[] {localPort}, new int[] {remotePort}, "uname -a");
    tunnel.start();
  }
  
}