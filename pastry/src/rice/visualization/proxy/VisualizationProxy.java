package rice.visualization.proxy;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.security.*;
import rice.p2p.multiring.*;

import rice.pastry.dist.DistNodeHandle;
import rice.pastry.dist.DistPastryNodeFactory;
import rice.visualization.*;
import rice.visualization.Visualization;

public class VisualizationProxy {
    
  protected Ring[] handles;
  
  protected DistPastryNodeFactory factory;
  
  protected Visualization visualization;
    
  public VisualizationProxy(String[] args) {
    parseArgs(args);
  }
  
  public void start() {
    visualization = new Visualization(handles);

//    visualization = new Visualization(handle);
  }
  
  public void parseArgs(String[] args) {
    int protocol = DistPastryNodeFactory.PROTOCOL_SOCKET;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-protocol") && i+1 < args.length) {
        String s = args[i+1];
        
        if (s.equalsIgnoreCase("wire"))
          protocol = DistPastryNodeFactory.PROTOCOL_WIRE;
        else if (s.equalsIgnoreCase("rmi"))
          protocol = DistPastryNodeFactory.PROTOCOL_RMI;
        else if (s.equalsIgnoreCase("socket"))
          protocol = DistPastryNodeFactory.PROTOCOL_SOCKET;
        else
          System.out.println("ERROR: Unsupported protocol: " + s);
        
        break;
      }
    } 
    
    factory = DistPastryNodeFactory.getFactory(null, protocol, 0);
    
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
          
          Ring r = new Ring(ringName, pair, (DistNodeHandle) factory.generateNodeHandle(new InetSocketAddress(bootstrap_host, bootstrap_port)), globalRing);
          rings.add(r);
          if (globalRing == null) { // this logic makes the global ring the first ring
            globalRing = r;
          }
        }
        handles = (Ring[]) rings.toArray(new Ring[0]);
//        for (int i = 0; i < handles.length; i++) {
//          System.out.println(handles[i]);
//        }        
      } catch (Exception e) {
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
  
  public static void main(String[] args) {
    VisualizationProxy proxy = new VisualizationProxy(args);
    proxy.start();
  }
  
}