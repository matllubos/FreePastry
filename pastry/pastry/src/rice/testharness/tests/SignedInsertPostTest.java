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

package rice.testharness.tests;

import rice.*;

import rice.pastry.client.*;
import rice.pastry.leafset.*;
import rice.pastry.security.*;
import rice.pastry.messaging.*;
import rice.pastry.multiring.*;
import rice.pastry.routing.*;
import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.wire.*;
import rice.pastry.standard.*;

import rice.scribe.*;
import rice.scribe.messaging.*;

import rice.past.*;

import rice.persistence.*;

import rice.post.*;
import rice.post.log.*;
import rice.post.storage.*;

import rice.testharness.*;
import rice.testharness.messaging.*;

import java.util.*;
import java.io.*;
import java.net.*;
import java.security.*;


public class SignedInsertPostTest extends PostTest {

  public static int[] FILE_SIZES = {1000, 1000, 10000, 100000, 1000000};

  private Random rng;

  private RandomRingNodeIdFactory factory;
  
  /**
    * Constructor which takes the local node this test is on,
    * an array of all the nodes in the network, and a printwriter
    * to which to write data.
    *
    * @param out The PrintWriter to write test results to.
    * @param localNode The local Pastry node
    * @param nodes NodeHandles to all of the other participating
    *              TestHarness nodes (this test class ignores these)
    */
  public SignedInsertPostTest(PrintStream out, PastryNode localNode, TestHarness harness) {
    super(out, localNode, harness);

    rng = new Random();
    factory = new RandomRingNodeIdFactory(((RingNodeId) localNode.getNodeId()).getRingId());
  }	

  /**
    * Method which is called when the TestHarness wants this
    * Test to begin testing.
    */
  public void startTest(NodeHandle[] nodes) {
    super.startTest(nodes);
    
    Continuation run = new Continuation() {
      int i=-1;
      int size=0;
      long[][] times = new long[FILE_SIZES.length][NUM_TRIALS];
      long beginTime;
      
      public void receiveResult(Object o) {
        if (i == -1) {
          i++;
        } else {
          times[size][i] = System.currentTimeMillis() - beginTime;
          System.out.println("Set " + FILE_SIZES[size] + " " + i + " to be " + times[size][i]);
          i++;
        }
          
        if (i < NUM_TRIALS) {
          byte[] data = new byte[FILE_SIZES[size]];
          rng.nextBytes(data);
          PostData postData = new SignedTestData(data);
          beginTime = System.currentTimeMillis();
          post.getStorageService().storeSigned(postData, factory.generateNodeId(), this);
        } else if (size < FILE_SIZES.length - 1) {
          size++;
          i=0;
          
          byte[] data = new byte[FILE_SIZES[size]];
          rng.nextBytes(data);
          PostData postData = new SignedTestData(data);
          beginTime = System.currentTimeMillis();
          post.getStorageService().storeSigned(postData, factory.generateNodeId(), this);
        } else {
          System.out.println("Signed:");

          for (int k=0; k<NUM_TRIALS; k++) {
            for (int j=0; j<FILE_SIZES.length; j++) {
              System.out.print(times[j][k] + "\t");
            }

            System.out.println();
          }
        }
      }

      public void receiveException(Exception e) {
        System.out.println("Exception " + e + " occurred while testing.");
        e.printStackTrace();
      }
    };

    if (nodes[0].getNodeId().equals(_localNode.getNodeId())) {
      run.receiveResult(null);
    }
  }
 
  public Address getAddress() {
    return SignedInsertPostTestAddress.instance();
  }

  public static class SignedTestData implements PostData {

    private byte[] data;
    
    public SignedTestData(byte[] data) {
      this.data = data;
    }

    public SignedReference buildSignedReference(NodeId location) {
      return new SignedReference(location);
    }

    public ContentHashReference buildContentHashReference(NodeId location, Key key) {
      throw new IllegalArgumentException("moNKEYS!");    }

    public SecureReference buildSecureReference(NodeId location, Key key) {
      throw new IllegalArgumentException("moNKEYS!");
    }
  }
    

  public static class SignedInsertPostTestAddress implements Address {

    /**
    * The only instance of SignedInsertPostTestAddress ever created.
     */
    private static SignedInsertPostTestAddress _instance;

    /**
    * Returns the single instance of SignedInsertPostTestAddress.
     */
    public static SignedInsertPostTestAddress instance() {
      if(null == _instance) {
        _instance = new SignedInsertPostTestAddress();
      }
      return _instance;
    }

    /**
      * Code representing address.
     */
    public int _code = 0x98834c98;

    /**
      * Private constructor for singleton pattern.
     */
    private SignedInsertPostTestAddress() {}

    /**
      * Returns the code representing the address.
     */
    public int hashCode() { return _code; }

    /**
      * Determines if another object is equal to this one.
     * Simply checks if it is an instance of SignedInsertPostTestAddress
     * since there is only one instance ever created.
     */
    public boolean equals(Object obj) {
      return (obj instanceof SignedInsertPostTestAddress);
    }
  }  
}