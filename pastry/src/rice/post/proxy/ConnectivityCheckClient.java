/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
package rice.post.proxy;

import java.io.*;
import java.net.*;
import java.util.zip.*;
import rice.proxy.*;
import rice.post.security.*;

public class ConnectivityCheckClient {
  
  protected InetSocketAddress server;
  
  protected InetAddress local;
  
  protected ConnectivityCheckClientClient client;
  
  public ConnectivityCheckClient(InetSocketAddress server) {
    this.server = server;
  }
  
  public static void main(String[] args) throws Exception {
    ConnectivityCheckClient c = new ConnectivityCheckClient(new InetSocketAddress(args[0], Integer.parseInt(args[1])));
    System.out.println("Checking ...");
    System.out.println("" + c.check(30000, 10001));
  }
  
  public InetAddress check(long timeout, int port) throws Exception {
     client = new ConnectivityCheckClientClient(server, port);

    synchronized (this) {
      client.start();
      
      System.out.println("Sleeping for " + timeout);
      this.wait(timeout);
    }
    
    System.out.println("Awake " + local);
    
    client.die();
    
    return local;
  }
  
  public String getError() {
    if ((! client.tcp) && (! client.udp))
      return "Received no response on either TCP or UDP.";
    else if (! client.tcp)
      return "Received no response on TCP.";
    else if (! client.tcp)
      return "Received no response on UDP.";
    else 
      return "Unknown error.";
  }
  
  protected class ConnectivityCheckClientClient extends Thread {
    
    protected InetSocketAddress server;
    
    protected int port;
    
    protected ServerSocket socket;
    
    protected DatagramSocket dsocket;
    
    protected boolean udp;
    
    protected boolean tcp;
    
    public ConnectivityCheckClientClient(InetSocketAddress address, int port) {      
      this.server = address;
      this.port = port;
    }
    
    public void run() {
      try {
        
        System.out.println("Bound to TCP/UDP port " + port);
        socket = new ServerSocket(port);
        dsocket = new DatagramSocket(port);
        
        send();
        
        System.out.println("Sent UDP request ");
        
        DatagramPacket packet = new DatagramPacket(new byte[32000], 32000);
        dsocket.receive(packet);
        
        udp = true;
        
        System.out.println("Received UDP response ");
        
        Socket s = socket.accept();
        
        ObjectInputStream ois = new ObjectInputStream(s.getInputStream());
        
        local = ((InetSocketAddress) ois.readObject()).getAddress();
          
        tcp = true;
        
        System.out.println("Sent TCP response " + local);
        
        synchronized (ConnectivityCheckClient.this) {
          ConnectivityCheckClient.this.notifyAll();
        }
      } catch (Exception e) {
        System.out.println("Got error " + e + " communicating with address " + server);
      }
    }
    
    protected void die() throws IOException {
      socket.close();
      dsocket.close();
    }
    
    protected void send() throws IOException {
      DatagramSocket datagram = new DatagramSocket();
      datagram.connect(server);
      
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      
      oos.writeInt(port);
      oos.close();
      
      DatagramPacket packet = new DatagramPacket(baos.toByteArray(), baos.toByteArray().length);
      datagram.send(packet);
      
      datagram.close();
    }
      
  }
}
