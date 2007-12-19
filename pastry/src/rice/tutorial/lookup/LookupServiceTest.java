package rice.tutorial.lookup;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;

import rice.Continuation;
import rice.environment.Environment;
import rice.p2p.commonapi.NodeHandleSet;
import rice.pastry.*;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.standard.RandomNodeIdFactory;

/**
 * This is an example of using the LookupService, based on the code for tutorial 4.
 * 
 * @author jstewart
 *
 */
public class LookupServiceTest {

  /**
   * Liberally copied from lesson4's DistTutorial.
   * 
   * @param bindport
   *                the local port to bind to
   * @param bootaddress
   *                the IP:port of the node to boot from
   * @param env
   *                the environment for these nodes
   */
  public LookupServiceTest(int bindport, InetSocketAddress bootaddress, int numNodes, Environment env) throws Exception {

    ArrayList<LookupService> lookups = new ArrayList<LookupService>();

    // Generate the NodeIds Randomly
    NodeIdFactory nidFactory = new RandomNodeIdFactory(env);

    // construct the PastryNodeFactory, this is how we use rice.pastry.socket
    PastryNodeFactory factory = new SocketPastryNodeFactory(nidFactory, bindport, env);

    // loop to construct the nodes/apps
    for (int curNode = 0; curNode < numNodes; curNode++) {
      // This will return null if we there is no node at that location
      NodeHandle bootHandle = ((SocketPastryNodeFactory) factory).getNodeHandle(bootaddress);

      // construct a node, passing the null boothandle on the first loop will
      // cause the node to start its own ring
      PastryNode node = factory.newNode(bootHandle);

      // the node may require sending several messages to fully boot into the
      // ring
      synchronized (node) {
        while (!node.isReady() && !node.joinFailed()) {
          // delay so we don't busy-wait
          node.wait(500);

          // abort if can't join
          if (node.joinFailed()) {
            throw new IOException("Could not join the FreePastry ring.  Reason:" + node.joinFailedReason());
          }
        }
      }

      System.out.println("Finished creating new node " + node);

      LookupService ls = new LookupService(node);

      lookups.add(ls);
    }

    // wait 10 seconds
    env.getTimeSource().sleep(10000);

    for (LookupService ls : lookups) {
      final Id id = nidFactory.generateNodeId();
      System.out.println("Requesting id "+id);
      ls.requestNodeHandles(id, 3, new Continuation<NodeHandleSet,Exception>() {
        public void receiveException(Exception exception) {
          if (exception instanceof LookupService.NodeLookupTimeoutException) {
            System.out.println("Request for "+id+" timed out");
          } else {
            System.out.println("Exception requesting "+id+": "+exception.getMessage());
            exception.printStackTrace();
          }
        }

        public void receiveResult(NodeHandleSet result) {
          System.out.println("ReplicaSet for "+id+": "+result);
        }
      });
    }
  }

  /**
   * Usage: java [-cp FreePastry-<version>.jar]
   * rice.tutorial.lookup.LookupServiceTest localbindport bootIP bootPort
   * numNodes example java rice.tutorial.lookup.LookupServiceTest 9001
   * pokey.cs.almamater.edu 9001 10
   * 
   * @throws Exception
   *                 when something goes wrong
   */
  public static void main(String[] args) throws Exception {
    // Loads pastry settings
    Environment env = new Environment();

    // disable the UPnP setting (in case you are testing this on a NATted LAN)
    env.getParameters().setString("nat_search_policy", "never");

    try {
      // the port to use locally
      int bindport = Integer.parseInt(args[0]);

      // build the bootaddress from the command line args
      InetAddress bootaddr = InetAddress.getByName(args[1]);
      int bootport = Integer.parseInt(args[2]);
      InetSocketAddress bootaddress = new InetSocketAddress(bootaddr, bootport);

      // the number of nodes to use
      int numNodes = Integer.parseInt(args[3]);

      // launch our node!
      LookupServiceTest lt = new LookupServiceTest(bindport, bootaddress, numNodes, env);
    } catch (Exception e) {
      // remind user how to use
      System.out.println("Usage:");
      System.out
          .println("java [-cp FreePastry-<version>.jar] rice.tutorial.lookup.LookupServiceTest localbindport bootIP bootPort numNodes");
      System.out.println("example java rice.tutorial.lookup.LookupServiceTest 9001 pokey.cs.almamater.edu 9001 10");
      throw e;
    }
  }
}
