package rice.pastry.testing;

import java.io.*;
import java.net.*;
import java.util.*;

import rice.environment.Environment;
import rice.environment.logging.Logger;
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
  
  protected SocketPastryNodeFactory factory;
  
  protected InetSocketAddress bootstrap;
  
  protected HashSet nodes;
  
  protected HashSet dead;
  
  protected HashSet unknown;
  
  protected final int MAX_THREADS = 100;
  
  protected Environment environment;
  
  public PastryNetworkTest(Environment env, SocketPastryNodeFactory factory, InetSocketAddress bootstrap) {
    this.environment = env;
    this.factory = factory;
    this.bootstrap = bootstrap;
    this.nodes = new HashSet();
    this.dead = new HashSet();
    this.unknown = new HashSet();
  }
  
  int numThreads = 0;
  
  protected HashMap fetchLeafSets() throws Exception {
    final HashMap leafsets = new HashMap();
    final HashSet unseen = new HashSet();
    
    final PrintStream ps = new PrintStream(new FileOutputStream("response.txt"));
    
    unseen.add(((DistPastryNodeFactory) factory).getNodeHandle(bootstrap));

    synchronized (unseen) {
      while (true) {
        if (numThreads >= MAX_THREADS) 
          unseen.wait();
        
        if (unseen.size() > 0) {
          numThreads++;
          
          final SocketNodeHandle handle = (SocketNodeHandle) unseen.iterator().next();          
          unseen.remove(handle);
          nodes.add(handle);
          System.out.println("Fetching leafset of " + handle + " (thread " + numThreads + " of "+MAX_THREADS+")");
          
          Thread t = new Thread() {
            public void run() {  
              boolean gotResponse = false;
              try {
                LeafSet ls = factory.getLeafSet(handle);
                System.out.println("Response:"+handle+" "+ls);
                gotResponse = true;
                ps.println(handle.getEpochAddress().getAddress().getAddress().getHostAddress()+":"+handle.getEpochAddress().getAddress().getPort());
        //        SourceRoute[] routes = factory.getRoutes(handle);
                
        //        for (int i=0; i<routes.length; i++) 
        //          System.out.println("ROUTE:\t" + routes[i].prepend(handle.getEpochAddress()));
                
                leafsets.put(handle, ls);
                
                NodeSet ns = ls.neighborSet(Integer.MAX_VALUE);
                
                if (! ns.get(0).equals(handle)) {
                  dead.add(handle); 
                  nodes.remove(handle);
                  leafsets.remove(handle);
                  leafsets.put(ns.get(0), ls);
                }
                
                for (int i=1; i<ns.size(); i++) 
                  if ((! nodes.contains(ns.get(i))) && (! dead.contains(ns.get(i))))
                    unseen.add(ns.get(i));
              
              } catch (java.net.ConnectException e) {
                dead.add(handle);
              } catch (java.net.SocketTimeoutException e) {
                unknown.add(handle);
              } catch (IOException e) {
                System.out.println("GOT OTHER ERROR CONNECTING TO " + handle + " - " + e);
              } finally {
                if (!gotResponse) {
                  System.out.println("Did not hear from "+handle); 
                }
                synchronized (unseen) {
                  numThreads--;
                  unseen.notifyAll();
                }
              }
            }
          };
          
          t.start();
        } else if (numThreads > 0) {
          unseen.wait();
        } else {
          break;
        }
      }
    }
    
    System.out.println("Fetched all leafsets - return...  Found " + nodes.size() + " nodes.");
    
    return leafsets;
  }
  
  protected void testLeafSets() throws Exception {
    HashMap leafsets = fetchLeafSets();

    Iterator sets = leafsets.values().iterator();
    
    while (sets.hasNext()) {
      Iterator nodes = leafsets.keySet().iterator();
      LeafSet set = (LeafSet) sets.next();
      
      if (set != null) {
        while (nodes.hasNext()) {
          NodeHandle node = (NodeHandle) nodes.next();
          
          if (dead.contains(node) && set.member(node)) {
            System.out.println("LEAFSET ERROR: Leafset for " + set.get(0) + " contains dead node " + node);
          } else if ((! dead.contains(node)) && set.isComplete() && set.test(node)) {
            System.out.println("LEAFSET ERROR: Leafset for " + set.get(0) + " is missing " + node);
          }
        }
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
      
      RouteSet[] set = factory.getRouteRow(handle, row);
      
      if (set != null)
        routerows.put(handle, set);        
    }
    
    System.out.println("Fetched all route rows - return...");
    
    return routerows;
  }
  
  protected void testRouteRow(int row) throws IOException {
    HashMap routerows = fetchRouteRow(row);
    
    Iterator i = nodes.iterator();
    
    while (i.hasNext()) {
      NodeHandle node = (NodeHandle) i.next();
      RoutingTable rt = new RoutingTable(node, 1, environment.getParameters().getInt("pastry_rtBaseBitLength"));
      
      Iterator j = nodes.iterator();

      while (j.hasNext())
        rt.put((NodeHandle) j.next());

      RouteSet[] ideal = (RouteSet[]) rt.getRow(row);
      RouteSet[] actual = (RouteSet[]) routerows.get(node);
      
      for (int k=0; k<ideal.length; k++) {
        if (((actual[k] == null) || (actual[k].size() == 0)) && ((ideal[k] != null) && (ideal[k].size() > 0)))
          System.out.println("ROUTING TABLE ERROR: " + node + " has no entry in row " + row + " column " + k + " but " + ideal[k].get(0) + " exists");

        if (((actual[k] != null) && (actual[k].size() > 0)) && ((ideal[k] == null) || (ideal[k].size() == 0)))
          System.out.println("ROUTING TABLE ERROR: " + node + " has no non-existent entry in row " + row + " column " + k + " entry " + actual[k].get(0) + " exists");
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
    //testRoutingTables();
    System.exit(0);
  }
  
  public static void main(String[] args) throws Exception {
//    PrintStream ps = new PrintStream(new FileOutputStream("lses.txt"));
//    System.setOut(ps);
//    System.setErr(ps);
    Environment env = new Environment();
    PastryNetworkTest test = new PastryNetworkTest(env, new SocketPastryNodeFactory(null, 1, env), new InetSocketAddress(args[0], Integer.parseInt(args[1])));
    test.start();
  }
}
