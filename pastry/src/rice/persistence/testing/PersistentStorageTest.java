
package rice.persistence.testing;

/*
 * @(#) PersistentStorageTest.java
 *
 * @author Ansley Post
 * @author Alan Mislove
 * 
 * @version $Id$
 */
import java.io.*;
import java.util.*;

import rice.*;
import rice.p2p.commonapi.*;
import rice.pastry.commonapi.*;
import rice.persistence.*;

/**
 * This class is a class which tests the PersistentStorage class
 * in the rice.persistence package.
 */
public class PersistentStorageTest extends MemoryStorageTest {

  private static IdFactory FACTORY = new PastryIdFactory();

  /**
   * Builds a MemoryStorageTest
   */
  public PersistentStorageTest(boolean store) throws IOException {
    super(store);
    storage = new PersistentStorage(FACTORY, "PersistentStorageTest" , ".", 20000000);
  }

  public static void main(String[] args) throws IOException {
    boolean store = true;

    if (args.length > 0) {
      store = ! args[0].equals("-nostore");
    }
    
    PersistentStorageTest test = new PersistentStorageTest(store);
    
    test.start();
  }
}
