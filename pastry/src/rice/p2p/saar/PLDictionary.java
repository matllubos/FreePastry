/*
 * Created on Nov 20, 2006
 */
package rice.p2p.saar;

import java.io.*;
import java.util.*;

import rice.p2p.commonapi.NodeHandle;

public class PLDictionary {
  public static final int NUMPLANETLABNODES = 600;
  public static PLNodeState[] plNodes = new PLNodeState[NUMPLANETLABNODES];

  
    public static Hashtable nameToPLIndex = new Hashtable();

    public static Hashtable ipToPLIndex = new Hashtable(); // This maps the 1.b.c.d representation
                                        // to PLIndex

  public static void initialize() {
    // We will store the Name to number mapping of the planetlab nodes
    nameToPLIndex = new Hashtable();
    ipToPLIndex = new Hashtable();
    FileReader fr = null;
    boolean fileEnded = false;

    try {
      fr = new FileReader(SaarTest.NAMETOIPCODEDFILE);
      BufferedReader in = new BufferedReader(fr);

      while (!fileEnded) {
        String line = null;
        line = in.readLine();
        if (line.equals("Done")) {
          fileEnded = true;
        } else {
          // Extract the node name from there
          String[] args = line.split(" ");
          int plIndex = Integer.parseInt(args[0]);
          String nodeName = args[1];
          String ip = args[2];
          plNodes[plIndex] = new PLNodeState(plIndex, nodeName, ip);
          ipToPLIndex.put(ip, plNodes[plIndex]);
          nameToPLIndex.put(nodeName, plNodes[plIndex]);
        }
      }
    } catch (Exception err) {
      System.out.println("ERROR : SocketPastryNode() While reading the "
          + SaarTest.NAMETOIPCODEDFILE + " " + err);
    }

//    if (SaarTest.logLevel <= 875)
      System.out.println("Number of nodes in SaarTest.NAMETOIPCODEDFILE: "
          + ipToPLIndex.size());

    String hostAddress = "NONE";
  }

  // WeakHashMap<PastryNode,Integer> map = new ;

  public static class PLNodeState {
    public int plIndex;

    public String nodeName;

    public String ip;

    public PLNodeState(int plIndex, String nodeName, String ip) {
      this.plIndex = plIndex;
      this.nodeName = nodeName;
      this.ip = ip;
      System.out.println(this);
    }

    public String toString() {
      String s = "PLNodeState: ";
      s = s + plIndex + ", " + nodeName + ", " + ip;
      return s;
    }
  }

  // Return '-1' if node's entry is missing
  public static int getPLIndexByName(String nodeName) {
    PLNodeState state = (PLNodeState)nameToPLIndex.get(nodeName);
    if(state == null) {
        //System.out.println("ERROR: getPLIndexByName(" + nodeName + ") failed");
        return -1;
    } else {
        return state.plIndex;
    }
    
  }

  // Return '-1' if node's entry is missing
  public static int getPLIndexByIp(String ipString) {
    PLNodeState state = (PLNodeState)ipToPLIndex.get(ipString);
    if(state == null) {
        //System.out.println("ERROR: getPLIndexByIp(" + ipString + ") failed");
        return -1;
    } else {
        return state.plIndex;
    }
    
  }
  
  public boolean containsPLIndex(int plIndex) {
    if(plIndex == -1) {
        return false;
    } else {
        PLNodeState state = plNodes[plIndex];
        if(state == null) {
        return false;
        } else {
        return true;
        }
    }
  }

 

  // Returns "NONE" if nodes's entry is missing
  public String getPLName(int plIndex) {
    //System.out.println("getPLName(" + plIndex + ") called");
    if(plIndex == -1) {
        return "NONE";
    } else {
        PLNodeState state = plNodes[plIndex];
        if(state == null) {
        //System.out.println("ERROR: getPLName(" + plIndex + ") failed");
        return "NONE";
        } else {
        return state.nodeName;
        }
    }
  }


   // Returns "NONE" if nodes's entry is missing
  public String getPLIp(int plIndex) {
    if(plIndex == -1) {
        return "NONE";
    } else {
        PLNodeState state = plNodes[plIndex];
        if(state == null) {
        //System.out.println("ERROR: getPLIp(" + plIndex + ") failed");
        return "NONE";
        } else {
        return state.ip;
        }
    }
  }  
}
