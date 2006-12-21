/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/
package rice.post.proxy;

import java.io.*;
import java.net.*;
import java.util.zip.*;
import rice.proxy.*;
import rice.post.security.*;

public class ConnectivityCheckServer {
  
  protected int port;
  
  protected int count;
  
  public ConnectivityCheckServer(int port) {
    this.port = port;
    this.count = 0;
  }
  
  public void start() {
    try {
      System.out.println("Binding in TCP/UDP on port " + port);
      DatagramSocket server = new DatagramSocket(port);
      DatagramPacket packet = new DatagramPacket(new byte[32000], 32000);
      
      while (true) {
        try {
          server.receive(packet);
          System.out.println("Received packet from " + packet.getAddress());

          ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(packet.getData()));
        
          new ConnectivityCheckClient(new InetSocketAddress(packet.getAddress(), ois.readInt()), count++).start();
        } catch (Exception e) {
          System.err.println("Got error " + e);          
        }
      }
    } catch (Exception e) {
      System.err.println("Got error " + e);
    }
  }
  
  public static void main(String[] args) {
    new ConnectivityCheckServer(Integer.parseInt(args[0])).start();
  }
  
  protected class ConnectivityCheckClient extends Thread {
    
    protected InetSocketAddress address;

    protected int id;
    
    public ConnectivityCheckClient(InetSocketAddress address, int id) {      
      this.address = address;
      this.id = id;
    }
    
    public void run() {
      try {
        DatagramSocket datagram = new DatagramSocket();
        datagram.connect(address);
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        
        oos.writeObject(address);
        oos.close();
        
        DatagramPacket packet = new DatagramPacket(baos.toByteArray(), baos.toByteArray().length);
        datagram.send(packet);
        
        datagram.close();
        
        System.out.println("Sent UDP response " + address);

        
        Socket socket = new Socket();
        socket.connect(address);
        
        System.out.println("Made TCP connection to " + address);
        ObjectOutputStream oos2 = new ObjectOutputStream(socket.getOutputStream());
        
        oos2.writeObject(address);
        oos2.close();
        
        System.out.println("Sent TCP response " + address);
        
        socket.close();
      } catch (Exception e) {
        System.out.println("Got error " + e + " communicating with address " + address);
      }
    }
  }
}
