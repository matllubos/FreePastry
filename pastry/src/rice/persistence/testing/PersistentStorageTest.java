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
 * This class is a class which tests the Storage class
 * in the rice.persistence package.
 */
public class PersistentStorageTest extends Test {
  
  private Storage storage;

  /**
   * Builds a PersistentStorageTest
   */
  public PersistentStorageTest() {
    storage = new PersistentStorage(".", 1000000000);
  }

  public void setUp(final Continuation c) {
    final Continuation put4 = new Continuation() {
      public void receiveResult(Object o) {
        if (o.equals(new Boolean(true))) {
          stepDone(SUCCESS);
        } else {
          stepDone(FAILURE, "Fourth object was not inserted.");
          return;
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
          stepDone(FAILURE, "Third object was not inserted.");
          return;
        }

        stepStart("Storing Fourth Object");
        storage.store(new Integer(4), "Fourth Object", put4);
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
          stepDone(FAILURE, "Second object was not inserted.");
          return;
        }

        stepStart("Storing Third Object");
        storage.store(new Integer(3), "Third Object", put3);
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
          stepDone(FAILURE, "First object was not inserted.");
          return;
        }

        stepStart("Storing Second Object");
        storage.store(new Integer(2), "Second Object", put2);
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };

    sectionStart("Storing Objects");
    
    stepStart("Storing First Object");
    storage.store(new Integer(1), "First Object", put1);
  }

  public void testRetreival(final Continuation c) {
    final Continuation get5 = new Continuation() {
      public void receiveResult(Object o) {
        if (o == null) {
          stepDone(SUCCESS);
        } else {
          stepDone(FAILURE, "Fifth object was returned (should not be present).");
          return;
        }

        sectionEnd();
        c.receiveResult(new Boolean(true));
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };

    final Continuation get4 = new Continuation() {
      public void receiveResult(Object o) {
        if (o == null) {
          stepDone(FAILURE, "Returned object was null.");
          return;
        }
        
        if (o.equals("Fourth Object")) {
          stepDone(SUCCESS);
        } else {
          stepDone(FAILURE, "Returned object was not correct: " + o);
          return;
        }

        stepStart("Attempting Fifth Object");
        storage.getObject(new Integer(5), get5);
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };
    
    final Continuation get3 = new Continuation() {
      public void receiveResult(Object o) {
        if (o == null) {
          stepDone(FAILURE, "Returned object was null.");
          return;
        }
        
        if (o.equals("Third Object")) {
          stepDone(SUCCESS);
        } else {
          stepDone(FAILURE, "Returned object was not correct: " + o);
          return;
        }

        stepStart("Retrieving Fourth Object");
        storage.getObject(new Integer(4), get4);
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };

    final Continuation get2 = new Continuation() {
      public void receiveResult(Object o) {
        if (o == null) {
          stepDone(FAILURE, "Returned object was null.");
          return;
        }
        
        if (o.equals("Second Object")) {
          stepDone(SUCCESS);
        } else {
          stepDone(FAILURE, "Returned object was not correct: " + o);
          return;
        }

        stepStart("Retrieving Third Object");
        storage.getObject(new Integer(3), get3);
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };

    final Continuation get1 = new Continuation() {
      public void receiveResult(Object o) {
        if (o == null) {
          stepDone(FAILURE, "Returned object was null.");
          return;
        }
        
        if (o.equals("First Object")) {
          stepDone(SUCCESS);
        } else {
          stepDone(FAILURE, "Returned object was not correct: " + o);
          return;
        }

        stepStart("Retrieving Second Object");
        storage.getObject(new Integer(2), get2);
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };
    
    Continuation get0 = new Continuation() {
      public void receiveResult(Object o) {
        if (o.equals(new Boolean(true))) {
          sectionStart("Retrieving Objects");

          stepStart("Retrieving First Object");
          storage.getObject(new Integer(1), get1);
        } else {
          stepException(new RuntimeException("SetUp did not complete correctly."));
        }
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };
 
    setUp(get0);
  }

  public void testExists(final Continuation c) {
    final Continuation check5 = new Continuation() {
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

        stepStart("Checking for Fifth Object");
        storage.exists(new Integer(5), check5);
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };
    
    final Continuation check3 = new Continuation() {
      public void receiveResult(Object o) {
        if (o.equals(new Boolean(true))) {
          stepDone(SUCCESS);
        } else {
          stepDone(FAILURE);
        }

        stepStart("Checking for Fourth Object");
        storage.exists(new Integer(4), check4);
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };

    final Continuation check2 = new Continuation() {
      public void receiveResult(Object o) {
        if (o.equals(new Boolean(true))) {
          stepDone(SUCCESS);
        } else {
          stepDone(FAILURE);
        }

        stepStart("Checking for Third Object");
        storage.exists(new Integer(3), check3);
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };

    final Continuation check1 = new Continuation() {
      public void receiveResult(Object o) {
        if (o.equals(new Boolean(true))) {
          stepDone(SUCCESS);
        } else {
          stepDone(FAILURE);
        }

        stepStart("Checking for Second Object");
        storage.exists(new Integer(2), check2);
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };

    Continuation check0 = new Continuation() {
      public void receiveResult(Object o) {
        if (o.equals(new Boolean(true))) {
          sectionStart("Checking for Objects");

          stepStart("Checking for First Object");
          storage.exists(new Integer(1), check1);
        } else {
          stepException(new RuntimeException("SetUp did not complete correctly."));
        }
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };

    testRetreival(check0);
  }  

  private void testRemoval(final Continuation c) {
    final Continuation done1 = new Continuation() {
      public void receiveResult(Object o) {
        if (o == null) {
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
    
    
    final Continuation retrieve1 = new Continuation() {
      public void receiveResult(Object o) {
        if (o.equals(new Boolean(false))) {
          stepDone(SUCCESS);
        } else {
          stepDone(FAILURE);
        }

        stepStart("Attempting to Retrieve First Object");
        storage.getObject(new Integer(1), done1);
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };
    
    final Continuation check1 = new Continuation() {
      public void receiveResult(Object o) {
        if (o.equals(new Boolean(true))) {
          stepDone(SUCCESS);
        } else {
          stepDone(FAILURE);
        }

        stepStart("Checking for First Object");
        storage.exists(new Integer(1), retrieve1);
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };

    Continuation remove1 = new Continuation() {
      public void receiveResult(Object o) {
        if (o.equals(new Boolean(true))) {
          sectionStart("Testing Removal");

          stepStart("Removing First Object");
          storage.unstore(new Integer(1), check1);
        } else {
          stepException(new RuntimeException("Exists did not complete correctly."));
        }
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };

    testExists(remove1);
  }

  private void testScan() {
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

        if (result.length != 2) {
          stepDone(FAILURE, "Result had " + result.length + " elements, expected 2.");
          return;
        }

        Arrays.sort(result);

        if (! ((result[0].equals(new Integer(3))) && (result[1].equals(new Integer(4))))) {
          stepDone(FAILURE, "Result had incorrect elements " + result[0] + ", " + result[1] + ", expected 3 and 4.");
          return;
        }

        stepDone(SUCCESS);
        
        stepStart("Requesting Scan from 8 to 10");
        storage.scan(new Integer(8), new Integer(10), verify2);        
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
        storage.scan(new Integer(3), new Integer(4), verify);
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
          storage.store("Monkey", new Integer(4), query);
        } else {
          stepException(new RuntimeException("Removal did not complete correctly."));
        }
      }

      public void receiveException(Exception e) {
        stepException(e);
      }
    };

    testRemoval(insertString);
  }    
  
  public void start() {
    testScan();
  }

  public static void main(String[] args) {
    PersistentStorageTest test = new PersistentStorageTest();

    test.start();
  }
}
