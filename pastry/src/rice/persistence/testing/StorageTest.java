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
public class StorageTest extends Test {
  
  private Storage storage;

  /**
   * Builds a MemoryStorageTest
   */
  public StorageTest() {
    storage = new MemoryStorage();
  }

  public void setUp(final Continuation c) {
    final Continuation put4 = new Continuation() {
      public void receiveResult(Object o) {
        if (o.equals(new Boolean(true))) {
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
          stepDone(FAILURE);
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
          stepDone(FAILURE);
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
          stepDone(FAILURE);
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
        if (o.equals("Fourth Object")) {
          stepDone(SUCCESS);
        } else {
          stepDone(FAILURE);
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
        if (o.equals("Third Object")) {
          stepDone(SUCCESS);
        } else {
          stepDone(FAILURE);
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
        if (o.equals("Second Object")) {
          stepDone(SUCCESS);
        } else {
          stepDone(FAILURE);
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
        if (o.equals("First Object")) {
          stepDone(SUCCESS);
        } else {
          stepDone(FAILURE);
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

  private void testRemoval() {
    final Continuation done1 = new Continuation() {
      public void receiveResult(Object o) {
        if (o == null) {
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
  
  public void start() {
    testRemoval();
  }

  public static void main(String[] args) {
    StorageTest test = new StorageTest();

    test.start();
  }
}
