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
package rice.visualization.proxy;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.security.*;

import rice.environment.Environment;
import rice.p2p.multiring.*;

import rice.pastry.dist.DistNodeHandle;
import rice.pastry.dist.DistPastryNodeFactory;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.visualization.*;

public class VisualizationProxy {
    
  protected Ring[] handles;
  
  protected SocketPastryNodeFactory factory;
  
  protected Visualization visualization;
    
  protected Environment environment;
  
  public VisualizationProxy(String[] args, Environment env) throws IOException {
    this.environment = env;
    parseArgs(args);
  }
  
  public void start() {
    visualization = new Visualization(handles, environment);

//    visualization = new Visualization(handle);
  }
  
  public void parseArgs(String[] args) throws IOException {
    Environment env = new Environment();
    int protocol = DistPastryNodeFactory.PROTOCOL_SOCKET;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-protocol") && i+1 < args.length) {
        String s = args[i+1];
        
//        if (s.equalsIgnoreCase("wire"))
//          protocol = DistPastryNodeFactory.PROTOCOL_WIRE;
//        else if (s.equalsIgnoreCase("rmi"))
//          protocol = DistPastryNodeFactory.PROTOCOL_RMI;
//        else 
          if (s.equalsIgnoreCase("socket"))
          protocol = DistPastryNodeFactory.PROTOCOL_SOCKET;
        else
          System.out.println("ERROR: Unsupported protocol: " + s);
        
        break;
      }
    } 
    
    factory = DistPastryNodeFactory.getFactory(null, protocol, 0, env);
    
    String bootstrap_host = "localhost";
    int bootstrap_port = 5009;
    Ring globalRing = null;
    int bootStrapArg = findBootstrapArg(args)+1;    
    if (bootStrapArg != -1) {
      try {      
        ArrayList rings = new ArrayList();
        for (int i = bootStrapArg; i < args.length; i+=2) {
          String ringName = args[i];
          
          String str = args[i+1];
          int index = str.indexOf(':');
          if (index == -1) {
            bootstrap_host = str;
          } else {
            bootstrap_host = str.substring(0, index);
            int tmpport = Integer.parseInt(str.substring(index + 1));
            if (tmpport > 0) {
              bootstrap_port = tmpport;
            }
          }

          System.out.print("Please enter the keypair password for ring '" + ringName + "': ");
          System.out.flush();
          String pass = new BufferedReader(new InputStreamReader(System.in)).readLine().trim();
          
          KeyPair pair = RingCertificate.readKeyPair(ringName.toLowerCase(), pass);
          
          Ring r = new Ring(ringName, pair, (DistNodeHandle) factory.getNodeHandle(new InetSocketAddress(bootstrap_host, bootstrap_port)), globalRing);
          rings.add(r);
          if (globalRing == null) { // this logic makes the global ring the first ring
            globalRing = r;
          }
        }
        handles = (Ring[]) rings.toArray(new Ring[0]);
//        for (int i = 0; i < handles.length; i++) {
//          System.outt.println(handles[i]);
//        }        
      } catch (Exception e) {
        e.printStackTrace();
        System.out.println("Usage of -bootstrap is now:");  
        System.out.println("  -bootstrap ringName1 nodeAddress1:port1 ringName2 nodeAddress2:port2 ...");  
        System.out.println("  -bootstrap [list] must be the last args given");  
        System.out.println("  currently we only support a heirarchy depth of 2, global must be the first");  
        System.exit(1);
      }
    } 
  }
  
  public int findBootstrapArg(String[] args) {
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-bootstrap") && i+1 < args.length) {
        return i;
      }
    }    
    return -1;
  }
  
  public static void main(String[] args) throws IOException {
    Environment env = new Environment();
    VisualizationProxy proxy = new VisualizationProxy(args, env);
    proxy.start();
  }
  
}
