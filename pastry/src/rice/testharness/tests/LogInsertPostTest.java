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

import rice.testharness.*;
import rice.testharness.messaging.*;

import java.util.*;
import java.io.*;
import java.net.*;
import java.security.*;


public class LogInsertPostTest extends PostTest {

  public static int LOG_ENTRY_SIZE = 10000;
  
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
  public LogInsertPostTest(PrintStream out, PastryNode localNode, TestHarness harness) {
    super(out, localNode, harness);
  }	

  /**
    * Method which is called when the TestHarness wants this
    * Test to begin testing.
    */
  public void startTest(NodeHandle[] nodes) {
    super.startTest(nodes);
    
    Continuation run = new Continuation() {
      int i=-1;
      long[] times = new long[NUM_TRIALS];
      long beginTime;
      PostLog log;
      
      public void receiveResult(Object o) {
        if (i == -1) {
          log = (PostLog) o;
          i++;
        } else {
          times[i] = System.currentTimeMillis() - beginTime;
          i++;
        }
          
        if (i < NUM_TRIALS) {
          beginTime = System.currentTimeMillis();
          log.addLogEntry(new LogInsertLogEntry(i), this);
        } else {
          System.out.println("Log Entry:");
          
          for (int j=0; j<NUM_TRIALS; j++) {
            System.out.println(j + ":\t" + times[j]);
          }
        }
      }

      public void receiveException(Exception e) {
        System.out.println("Exception " + e + " occurred while fetching log.");
      }
    };

    if (nodes[0].getNodeId().equals(_localNode.getNodeId())) {
      post.getPostLog(run);
    }
  }
 
  public Address getAddress() {
    return LogInsertPostTestAddress.instance();
  }

  public static class LogInsertLogEntry extends LogEntry {

    private byte[] data;
    
    private int num;

    public LogInsertLogEntry(int num) {
      this.num = num;
      this.data = new byte[LOG_ENTRY_SIZE];
    }
    
  }

  public static class LogInsertPostTestAddress implements Address {

    /**
    * The only instance of LogInsertPostTestAddress ever created.
     */
    private static LogInsertPostTestAddress _instance;

    /**
    * Returns the single instance of LogInsertPostTestAddress.
     */
    public static LogInsertPostTestAddress instance() {
      if(null == _instance) {
        _instance = new LogInsertPostTestAddress();
      }
      return _instance;
    }

    /**
      * Code representing address.
     */
    public int _code = 0x98834c96;

    /**
      * Private constructor for singleton pattern.
     */
    private LogInsertPostTestAddress() {}

    /**
      * Returns the code representing the address.
     */
    public int hashCode() { return _code; }

    /**
      * Determines if another object is equal to this one.
     * Simply checks if it is an instance of LogInsertPostTestAddress
     * since there is only one instance ever created.
     */
    public boolean equals(Object obj) {
      return (obj instanceof LogInsertPostTestAddress);
    }
  }  
}