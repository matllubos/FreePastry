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

package rice.persistence.testing;

/*
 * @(#) StorageTest.java
 *
 * @author Ansley Post
 * @author Alan Mislove
 *
 * @version $Id$
 */
import java.util.*;

import rice.*;
import rice.persistence.*;

/**
 * This class is a class which tests the Cache class
 * in the rice.persistence package.
 */
public class CacheTest extends Test {

  protected static final int CACHE_SIZE = 100;
  
  private Cache cache;

  /**
   * Builds a MemoryStorageTest
   */
  public CacheTest() {
    cache = new LRUCache(new MemoryStorage(), CACHE_SIZE);
  }

  public void setUp(final Continuation c) {
    final Continuation put4 = new Continuation() {
      public void receiveResult(Object o) {
        if (o.equals(new Boolean(false))) {
          stepDone(SUCCESS);
        } else {
          stepDone(FAILURE);
        }

        sectionEnd();
        c.receiveResult(new Boolean(true));
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };

    final Continuation put3 = new Continuation() {
      public void receiveResult(Object o) {
        if (o.equals(new Boolean(true))) {
          stepDone(SUCCESS);
        } else {
          stepDone(FAILURE);
        }

        stepStart("Inserting Fourth Object (227 bytes)");
        cache.cache(new Integer(4), new byte[200], put4);
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };    

    final Continuation put2 = new Continuation() {
      public void receiveResult(Object o) {
        if (o.equals(new Boolean(true))) {
          stepDone(SUCCESS);
        } else {
          stepDone(FAILURE);
        }

        stepStart("Inserting Third Object (65 bytes)");
        cache.cache(new Integer(3), new byte[38], put3);
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };

    Continuation put1 = new Continuation() {
      public void receiveResult(Object o) {
        if (o.equals(new Boolean(true))) {
          stepDone(SUCCESS);
        } else {
          stepDone(FAILURE);
        }

        stepStart("Inserting Second Object (40 bytes)");
        cache.cache(new Integer(2), new byte[13], put2);
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };

    sectionStart("Inserting Objects");
    
    stepStart("Inserting First Object (30 bytes)");
    cache.cache(new Integer(1), new byte[3], put1);
  }

  private void testExists(final Continuation c) {
    final Continuation done = new Continuation() {
      public void receiveResult(Object o) {
        if (o.equals(new Boolean(false))) {
          stepDone(SUCCESS);
        } else {
          stepDone(FAILURE);
        }

        sectionEnd();

        c.receiveResult(new Boolean(true));
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };


    final Continuation check4 = new Continuation() {
      public void receiveResult(Object o) {
        if (o.equals(new Boolean(true))) {
          stepDone(SUCCESS);
        } else {
          stepDone(FAILURE);
        }

        stepStart("Checking for Fourth Object");
        cache.exists(new Integer(4), done);
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };

    final Continuation check3 = new Continuation() {
      public void receiveResult(Object o) {
        if (o.equals(new Boolean(false))) {
          stepDone(SUCCESS);
        } else {
          stepDone(FAILURE);
        }

        stepStart("Checking for Third Object");
        cache.exists(new Integer(3), check4);
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };

    final Continuation check2 = new Continuation() {
      public void receiveResult(Object o) {
        if (o.equals(new Boolean(false))) {
          stepDone(SUCCESS);
        } else {
          stepDone(FAILURE);
        }

        stepStart("Checking for Second Object");
        cache.exists(new Integer(2), check3);
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };

    final Continuation check1 = new Continuation() {
      public void receiveResult(Object o) {
        if (o.equals(new Boolean(true))) {
          sectionStart("Checking for Objects");
          stepStart("Checking for First Object");
          cache.exists(new Integer(1), check2);
        } else {
          throw new RuntimeException("SetUp did not complete correctly!");
        }
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };

    setUp(check1);
  }

  private void testScan() {
    final Continuation handleBadScan = new Continuation() {
      public void receiveResult(Object o) {
        stepDone(FAILURE, "Query returned; should have thrown exception");
      }

      public void receiveException(Exception e) {
        stepDone(SUCCESS);

        sectionEnd();
      }
    };
    
    final Continuation verify2 = new Continuation() {
      public void receiveResult(Object o) {
        if (o == null) {
          stepDone(FAILURE, "Result of query was null");
          return;
        }

        Comparable[] result = (Comparable[]) o;

        if (result.length != 0) {
          stepDone(FAILURE, "Result had " + result.length + " elements, expected 0.");
          return;
        }

        stepDone(SUCCESS);

        stepStart("Requesting Scan from 'Monkey' to 9");
        cache.scan("Monkey", new Integer(9), handleBadScan);
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };

    final Continuation verify = new Continuation() {
      public void receiveResult(Object o) {
        if (o == null) {
          stepDone(FAILURE, "Result of query was null");
          return;
        }

        Comparable[] result = (Comparable[]) o;

        if (result.length != 1) {
          stepDone(FAILURE, "Result had " + result.length + " elements, expected 1.");
          return;
        }

        Arrays.sort(result);

        if (! result[0].equals(new Integer(3))) {
          stepDone(FAILURE, "Result had incorrect element " + result[0] + ", expected 3.");
          return;
        }

        stepDone(SUCCESS);

        stepStart("Requesting Scan from 8 to 10");
        cache.scan(new Integer(8), new Integer(10), verify2);
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };

    final Continuation query = new Continuation() {
      public void receiveResult(Object o) {
        if (o.equals(new Boolean(true))) {
          stepDone(SUCCESS);
        } else {
          stepDone(FAILURE);
        }

        stepStart("Requesting Scan from 3 to 6");
        cache.scan(new Integer(3), new Integer(6), verify);
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };

    Continuation insertString = new Continuation() {
      public void receiveResult(Object o) {
        if (o.equals(new Boolean(true))) {
          sectionStart("Testing Scan");

          stepStart("Inserting String as Key");
          cache.cache("M", new byte[0], query);
        } else {
          stepException(new RuntimeException("Exists did not complete correctly."));
        }
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };

    testExists(insertString);
  }    
  
  public void start() {
    testScan();
  }

  public static void main(String[] args) {
    CacheTest test = new CacheTest();

    test.start();
  }
}
