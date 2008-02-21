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
package rice.proxy;

import java.io.*;
import java.net.*;
import java.util.Date;
import java.util.zip.*;
import java.security.*;
import rice.p2p.util.*;
import rice.p2p.multiring.*;

public class NetworkLogServer {
  
  protected int port;
  
  protected int count;
  
  protected PrivateKey key;
  
  public static final int MAX_CLIENTS = 60;

  public int numClients = 0;
  
  public Object lock = new Object();
  
  public NetworkLogServer(PrivateKey key, int port) {
    this.port = port;
    this.key = key;
    this.count = 0;
  }
  
  public void start() {
    try {
      System.out.println("Starting server on port "+port);
      ServerSocket server = new ServerSocket(port,5);
      
      while (true) {
        if (numClients < MAX_CLIENTS) {
          new NetworkLogClient(server.accept(), count++).start();
          numClients++;
        } else {
          synchronized(lock) {
            lock.wait(5000); 
          }
        }
      }
    } catch (Exception e) {
      System.err.println("Got error " + e);
    }
  }
  
  public static void main(String[] args) throws Exception {
    // add cert stuff here
    String ring = args[0];
    String pass = args[1];
   
//    System.out.println("Starting "+new Date());
    try {
      KeyPair pair = RingCertificate.readKeyPair(ring.toLowerCase(), pass);
  //    System.out.println("Got keypair "+new Date());
      new NetworkLogServer(pair.getPrivate(), Integer.parseInt(args[2])).start();
    } catch (FileNotFoundException ioe) {
      new NetworkLogServer(null, Integer.parseInt(args[2])).start();
    }
  }
    
  protected class NetworkLogClient extends Thread {
    
    protected Socket socket;
    
    protected OutputStream out;
    
    protected long length;
    
    protected byte[] buffer = new byte[256000];
    
    protected int id;
    
    public NetworkLogClient(Socket socket, int id) {      
      this.socket = socket;
      this.id = id;
      System.out.println("Got connection from "+socket+ " at "+new Date());
    }
    
    protected void readHeader(DataInputStream in) {
      try {
        int len = (int) in.readLong();
        
        System.out.println(id + " Read length " + len);
        byte[] header = new byte[len];
        in.readFully(header);
        
        ByteArrayInputStream bais = new ByteArrayInputStream(header);
        ObjectInputStream ois = new ObjectInputStream(bais);
        
        String filename = ((String) ois.readObject()).replaceAll("/", "-");
        
        System.out.println(id + " Writing to file " + filename);
        
        File outPath = new File("./"+socket.getInetAddress().getHostAddress());
        outPath.mkdirs();
        File outFile = new File(outPath, filename + ".gz");
        
        out = new GZIPOutputStream(new FileOutputStream(outFile));
        length = ois.readLong();
        ois.close();
      } catch (Exception e) {
        System.out.println(id + " ERROR: Could not read header... " + e);
        throw new RuntimeException(e);
      }
    }
    
    public void run() {
      DataInputStream in = null;
      
      try {
        if (key != null) {
          in = new DataInputStream(new GZIPInputStream(new EncryptedInputStream(key, socket.getInputStream())));
        } else {
          in = new DataInputStream(new GZIPInputStream(socket.getInputStream()));
        }
        readHeader(in);
        
        int total = 0;
        int read = 0;
        
        while ((total < length) && ((read = in.read(buffer)) > 0)) {
          out.write(buffer, 0, read); 
          total += read;
        }
        
        System.out.println(id + " Done reading, sending confirmation...");
        
        if (read != -1) socket.getOutputStream().write((byte) 1); 
        System.out.println(id + " Done writing... (" + read + ")");
      } catch (IOException e) {
        System.err.println("ERROR: Got exception " + e + " while reading file - aborting!");
        e.printStackTrace();
      } finally {
        try {
          System.out.println(id + " Exiting");
          
          if (in != null) in.close();
          if (out != null) out.close();
          if (socket != null) socket.close();
        } catch (IOException e) {
          System.err.println("PANIC: Got exception " + e + " while closing streams!");
        } finally {
          numClients--;
          synchronized(lock) {
            lock.notifyAll(); 
          }
        }
      }
    }
  }
}
