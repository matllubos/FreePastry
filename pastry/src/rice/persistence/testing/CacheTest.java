package rice.persistence.testing;
/*
 * @(#) StorageTest.java
 *
 * @author Ansley Post
 * @author Alan Mislove
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

        stepStart("Inserting Third Object (77 bytes)");
        cache.cache(new Integer(3), new byte[50], put3);
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

        stepStart("Inserting Second Object (37 bytes)");
        cache.cache(new Integer(2), new byte[10], put2);
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };

    sectionStart("Inserting Objects");
    
    stepStart("Inserting First Object (27 bytes)");
    cache.cache(new Integer(1), new byte[0], put1);
  }

  private void testExists() {
    final Continuation done = new Continuation() {
      public void receiveResult(Object o) {
        if (o.equals(new Boolean(false))) {
          stepDone(SUCCESS);
        } else {
          stepDone(FAILURE);
        }

        sectionEnd();
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
  
  public void start() {
    testExists();
  }

  public static void main(String[] args) {
    CacheTest test = new CacheTest();

    test.start();
  }
}
