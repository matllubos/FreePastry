package rice.pastry.testing;

import java.io.*;
import java.net.*;
import java.util.*;

import rice.pastry.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;
import rice.pastry.dist.*;
import rice.pastry.socket.*;

/** 
 * Utility class for checking the consistency of an existing pastry
 * network.
 */
public class PastryNetworkTest {
  
  protected PastryNodeFactory factory;
  
  protected InetSocketAddress bootstrap;
  
  protected HashSet nodes;
  
  public PastryNetworkTest(PastryNodeFactory factory, InetSocketAddress bootstrap) {
    this.factory = factory;
    this.bootstrap = bootstrap;
    this.nodes = new HashSet();
  }
  
  protected HashMap fetchLeafSets() throws IOException {
    HashMap leafsets = new HashMap();
    HashSet unseen = new HashSet();
    
    unseen.add(((DistPastryNodeFactory) factory).getNodeHandle(bootstrap));
    
    while (unseen.size() > 0) {
      NodeHandle handle = (NodeHandle) unseen.iterator().next(); 
      unseen.remove(handle);
      nodes.add(handle);
      
      System.out.println("Fetching leafset of " + handle);

      try {
        LeafSet ls = factory.getLeafSet(handle);
        leafsets.put(handle, ls);
        
        NodeSet ns = ls.neighborSet(Integer.MAX_VALUE);
        
        for (int i=0; i<ns.size(); i++) {
          if (! nodes.contains(ns.get(i)))
            unseen.add(ns.get(i));
        }
      } catch (IOException ioe) {
        ioe.printStackTrace();
      }
    }
    
    System.out.println("Fetched all leafsets - return...");
    
    return leafsets;
  }
  
  protected void testLeafSets() throws IOException {
    HashMap leafsets = fetchLeafSets();
    
    Iterator nodes = leafsets.keySet().iterator();
    
    while (nodes.hasNext()) {
      NodeHandle node = (NodeHandle) nodes.next();
      Iterator sets = leafsets.values().iterator();
      
      while (sets.hasNext()) {
        LeafSet set = (LeafSet) sets.next();
        
        if (set.test(node)) 
          System.err.println("LEAFSET ERROR: " + node + " should appear in leafset for " + set.get(0));
      }
    }
    
    // check leafset sfor unknowns...
    
    System.out.println("Done testing...");
  }  
  
  protected HashMap fetchRouteRow(int row) throws IOException {
    HashMap routerows = new HashMap();
    Iterator i = nodes.iterator();
    
    while (i.hasNext()) {
      NodeHandle handle = (NodeHandle) i.next(); 
      
      System.out.println("Fetching route row " + row + " of " + handle);
      try {
        RouteSet[] set = factory.getRouteRow(handle, row);
        
        routerows.put(handle, set);      
      } catch (IOException ioe) {
        ioe.printStackTrace();
      }
    }
    
    System.out.println("Fetched all route rows - return...");
    
    return routerows;
  }
  
  protected void testRouteRow(int row) throws IOException {
    HashMap routerows = fetchRouteRow(row);
    
    Iterator i = nodes.iterator();
    
    while (i.hasNext()) {
      NodeHandle node = (NodeHandle) i.next();
      RoutingTable rt = new RoutingTable(node, 1);
      
      Iterator j = nodes.iterator();

      while (j.hasNext())
        rt.put((NodeHandle) j.next());

      RouteSet[] ideal = (RouteSet[]) rt.getRow(row);
      RouteSet[] actual = (RouteSet[]) routerows.get(node);
      
      for (int k=0; k<ideal.length; k++) {
        if (((actual[k] == null) || (actual[k].size() == 0)) && ((ideal[k] != null) && (ideal[k].size() > 0)))
          System.err.println("ROUTING TABLE ERROR: " + node + " has no entry in row " + row + " column " + k + " but " + ideal[k].get(0) + " exists");

        if (((actual[k] != null) && (actual[k].size() > 0)) && ((ideal[k] == null) || (ideal[k].size() == 0)))
          System.err.println("ROUTING TABLE ERROR: " + node + " has no non-existent entry in row " + row + " column " + k + " entry " + actual[k].get(0) + " exists");
      }
    }
    
    System.out.println("Done testing...");
  }  
    
  protected void testRoutingTables() throws Exception {
    testRouteRow(39);
    testRouteRow(38);
  }
  
  public void start() throws Exception {
    testLeafSets();
    testRoutingTables();
  }
  
  public static void main(String[] args) throws Exception {
    PastryNetworkTest test = new PastryNetworkTest(new SocketPastryNodeFactory(null, 1), new InetSocketAddress(args[0], Integer.parseInt(args[1])));
    test.start();
  }
}