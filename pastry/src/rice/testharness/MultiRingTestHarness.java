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

package rice.testharness;

import rice.pastry.client.*;
import rice.pastry.security.*;
import rice.pastry.messaging.*;
import rice.pastry.multiring.*;
import rice.pastry.routing.*;
import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.rmi.*;
import rice.pastry.standard.*;

import rice.scribe.*;
import rice.scribe.messaging.*;

import rice.testharness.messaging.*;

import java.util.*;
import java.io.*;
import java.net.*;
import java.rmi.*;
import java.lang.reflect.*;


/**
 * An extension of the test harness to work with multiple rings.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class MultiRingTestHarness extends TestHarness {

  protected MultiRingPastryNode node;
  
  protected MultiRingDistTestHarnessRunner runner;
  
  /**
   * Constructor which creates a TestHarness given a
   * PastryNode.
   *
   * @param pn The PastryNode this MultiRingTestHarness is running on.
   */
  public MultiRingTestHarness(PastryNode pn, MultiRingDistTestHarnessRunner runner) {
    super(pn);

    this.node = (MultiRingPastryNode) pn;
    this.runner = runner;
  }

  public Scribe generateScribe() {
    return ((MultiRingPastryNode) _pastryNode).getScribe();
  }
  
  public void receiveMessage( ScribeMessage msg ) {
    messageForAppl((Message) msg.getData());

    if (node.getRingId().equals(MultiRingPastryNode.GLOBAL_RING_ID)) {
      MultiRingTestHarness[] others = runner.getOthers();

      for (int i=0; i<others.length; i++) {
        if (others[i].node.getNodeId().equals(node.getNodeId())) {
          others[i].sendToAll((Message) msg.getData());
        }
      }
    }
  }
}
