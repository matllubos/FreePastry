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

import rice.pastry.PastryNode;
import rice.pastry.commonapi.*;
import rice.pastry.security.*;

import rice.p2p.past.*;
import rice.p2p.commonapi.*;

import rice.persistence.*;

import rice.testharness.*;
import rice.testharness.messaging.*;

import java.util.*;
import java.io.*;
import java.net.*;
import java.security.*;


/**
*
* @version $Id$
*
* @author Alan Mislove
*/

public class PastTest extends Test {

  public static int NUM_ITEMS = 100;

  public static int REPLICATION_FACTOR = 3;

  public static String INSTANCE = "PastTest";

  public static IdFactory FACTORY = new PastryIdFactory();

  public static int ID_BYTE_LENGTH = 20;

  public static int DATA_BYTE_LENGTH = 1000;

  protected Id[] ids;

  protected TestPastContent[] contents;

  protected Node node;
  
  protected Past past;

  protected StorageManager storage;

  protected Random random;

  protected boolean inserted;

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
  public PastTest(PrintStream out, PastryNode localNode, TestHarness harness) {
    super(out, localNode, harness, INSTANCE);

    storage = new StorageManager(FACTORY,
                                 new PersistentStorage(FACTORY, ".", 1000000),
                                 new LRUCache(new MemoryStorage(FACTORY), 1000000));
    past = new PastImpl(localNode, storage, REPLICATION_FACTOR, INSTANCE);
    random = new Random();

    ids = new Id[NUM_ITEMS];
    contents = new TestPastContent[NUM_ITEMS];
    inserted = false;
  }	

  /**
    * Method which is called when the TestHarness wants this
    * Test to begin testing.
    */
  public synchronized void startTest(rice.pastry.NodeHandle[] nodes) {
    if (! inserted) {
      Continuation insert = new Continuation() {
        private int num = -1;
        private byte[] idData = new byte[ID_BYTE_LENGTH];

        public void receiveResult(Object o) {
          if (! (o instanceof Boolean[])) {
            System.out.println("Insert " + num + " returned unexpected response - aborting.");
            return;
          }

          if (num < NUM_ITEMS - 1) {
            num++;
            random.nextBytes(idData);

            byte[] data = new byte[DATA_BYTE_LENGTH];
            random.nextBytes(data);
            
            ids[num] = FACTORY.buildId(idData);
            contents[num] = new TestPastContent(ids[num], data);

            System.out.println("Inserting item " + num + " at " + ids[num]);

            past.insert(contents[num], this);
          } else {
            System.out.println("Insert of " + num + " objects done.");
            inserted = true;
          }
        }

        public void receiveException(Exception e) {
          System.out.println("Insert " + num + " failed with exception " + e + " - aborting.");
        }
      };

      insert.receiveResult(new Boolean[0]);
    } else {
      Continuation verify = new Continuation() {
        private int num = 0;

        public void receiveResult(Object o) {
          if (o != null) {
            TestPastContent content = (TestPastContent) o;

            if (! Arrays.equals(contents[num].data, content.data)) {
              System.out.println("Lookup of " + num + " returned wrong data - aborting.");
            }
            
            num++;
          } else {
            System.out.println("Lookup of " + num + " failed - aborting.");
            return;
          }

          if (num < NUM_ITEMS) {
            past.lookup(ids[num], this);
          } else {
            System.out.println("Verification of " + num + " objects done.");
          }
        }

        public void receiveException(Exception e) {
          System.out.println("Verification " + num + " failed with exception " + e + " - aborting.");
        }
      };

      past.lookup(ids[0], verify);      
    }
  }

  public void messageForAppl(rice.pastry.messaging.Message msg) {
  }

  public Credentials getCredentials() {
    return new PermissiveCredentials();
  }

  protected static class TestPastContent implements PastContent {

    protected Id id;

    protected byte[] data;

    public TestPastContent(Id id, byte[] data) {
      this.id = id;
      this.data = data;
    }

    public PastContent checkInsert(Id id, PastContent existingContent) throws PastException {
      return this;
    }

    public PastContentHandle getHandle(Past past) {
      return new TestPastContentHandle(past, id);
    }

    public Id getId() {
      return id;
    }

    public boolean isMutable() {
      return true;
    }

    public boolean equals(Object o) {
      if (! (o instanceof TestPastContent)) return false;

      return ((TestPastContent) o).id.equals(id);
    }

    public String toString() {
      return "TestPastContent(" + id + ")";
    }
  }

  protected static class TestPastContentHandle implements PastContentHandle {

    protected NodeHandle handle;

    protected Id id;

    public TestPastContentHandle(Past past, Id id) {
      this.handle = past.getLocalNodeHandle();
      this.id = id;
    }

    public Id getId() {
      return id;
    }

    public NodeHandle getNodeHandle() {
      return handle;
    }
  }      
}
