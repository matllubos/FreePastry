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

package rice.pastry.testing;

import rice.pastry.*;
import rice.pastry.wire.*;
import rice.pastry.dist.*;
import rice.pastry.standard.*;
import rice.pastry.join.*;
import rice.pastry.client.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;

import java.util.*;
import java.net.*;

/**
 * a regression test suite for pastry with Sockets.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class WirePastryRegrTest extends PastryRegrTest {
  private static int bootstrapPort = 9000;
  private static String bootstrapHost = "verm.owlnet.rice.edu";

  private static int port = 9000;

  private InetSocketAddress bootstrap;

  // constructor
  public WirePastryRegrTest(String[] args) {
    super();

    initVars(args);

    factory = DistPastryNodeFactory.getFactory(DistPastryNodeFactory.PROTOCOL_WIRE, port);

    try {
      bootstrap = new InetSocketAddress(bootstrapHost, bootstrapPort);
    } catch (Exception e) {
      System.out.println("ERROR (doStuff): " + e);
    }
  }

  private void initVars(String[] args) {
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-help")) {
        System.out.println("Usage: WirePastryRegrTest [-bootstrap host[:port]] [-help]");
        System.exit(1);
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-bootstrap") && i+1 < args.length) {
        String str = args[i+1];
        int index = str.indexOf(':');
        if (index == -1) {
          bootstrapHost = str;
          bootstrapPort = port;
        } else {
          bootstrapHost = str.substring(0, index);
          int port = Integer.parseInt(str.substring(index + 1));
          if (port > 0) {
            bootstrapPort = port;
            this.port = port;
          }
        }

        break;
      }
    }
  }

  /**
   * make a new pastry node
   */
  protected PastryNode generateNode(NodeHandle bootstrap) {
    PastryNode pn = null;
    InetSocketAddress address = null;

    try {
      address = new InetSocketAddress(InetAddress.getLocalHost(), port);
    } catch (Exception e) {
      System.out.println("ERROR (doStuff): " + e);
    }

    pn = factory.newNode((WireNodeHandle) bootstrap);

    port+=2;

    return pn;
  }


  /**
   * Gets a handle to a bootstrap node. First tries localhost, to see
   * whether a previous virtual node has already bound itself. Then it
   * tries nattempts times on bshost:bsport.
   *
   * @return handle to bootstrap node, or null.
   */
  protected NodeHandle getBootstrap() {
    return ((DistPastryNodeFactory) factory).getNodeHandle(bootstrap);
  }

  /**
   * wire protocol specific handling of the application object
   * e.g., RMI may launch a new thread
   *
   * @param pn pastry node
   * @param app newly created application
   */
  protected void registerapp(PastryNode pn, RegrTestApp app) {
  }

  // do nothing in the RMI world
  public boolean simulate() { return false; }

  public boolean isReallyAlive(NodeId id) {
    // xxx
    return false;
  }

  protected void killNode(PastryNode pn) {
    ((WirePastryNode) pn).kill();
  }

  /**
   * Usage: WireRegrPastryTest [-bootstrap host[:port]] [-help]
   */
  public static void main(String args[]) {
    WirePastryRegrTest pt = new WirePastryRegrTest(args);
    mainfunc(pt, args, 10 /*n*/, 2/*d*/, 10/*k*/, 10/*m*/, 4/*conc*/);
  }
}
