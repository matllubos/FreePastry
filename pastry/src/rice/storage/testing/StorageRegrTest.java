package rice.storage.testing;

import rice.storage.*;

import rice.pastry.*;
import rice.pastry.standard.*;
import rice.pastry.security.*;

import ObjectWeb.Persistence.*;
//import ObjectWeb.Security.*;

import java.util.*;


/**
 * Provides regression testing for the StorageManager.
 *
 * @version $Id$
 * @author Charlie Reis
 */

public class StorageRegrTest {
  private PersistenceManager _pm;
  private RandomNodeIdFactory _idFactory;
  
  //private static final CredentialsFactory _credentialsFactory =
  //  new CredentialsFactory();

   
  public StorageRegrTest() {
    _pm = new DummyPersistenceManager();
    _idFactory = new RandomNodeIdFactory();
  }

  /**
   * Creates a credentials object to be used for an author.
   */
  //private static Credentials _createUserCredentials() {  
  //  return _credentialsFactory.createCredential(CredentialsFactory.USER_CREDENTIAL);
  //}
  
  
  /* ---------- Setup methods ---------- */

  /**
   * Sets up the environment for regression tests.
   */
  protected void initialize() {
  }
  
  
  /* ---------- Testing utility methods ---------- */
  
  /**
   * Throws an exception if the test condition is not met.
   */
  protected void assertTrue(String name, String intention, boolean test)
    throws TestFailedException
  {
    if (!test) {
      throw new TestFailedException("\nAssertion failed in '" + name +
                                    "'\nExpected: " + intention);
    }
  }

  /**
   * Thows an exception if expected is not equal to actual.
   */
  protected void assertEquals(String name,
                              String description,
                              Object expected,
                              Object actual)
    throws TestFailedException
  {
    if (!expected.equals(actual)) {
      throw new TestFailedException("\nAssertion failed in '" + name +
                                    "'\nDescription: " + description + 
                                    "\nExpected: " + expected + 
                                    "\nActual: " + actual);
    }
  }
  
  
  /* ---------- Test methods ---------- */

  /**
   * Tests inserting, retrieving, updating, and deleting a single file.
   */
  protected void testSingleFile() throws TestFailedException {
    StorageManager sm = new StorageManagerImpl(_pm);
    Credentials userCred = new Credentials() {};
    
    // Store file1
    NodeId id1 = _idFactory.generateNodeId();
    Persistable file1 = new DummyPersistable("file 1");
    assertTrue("SingleFile", "File should store successfully",
               sm.insert(id1, file1, userCred));
    
    // Retrieve file1
    StorageObject result = sm.lookup(id1);
    assertTrue("SingleFile", "Retrieved result should not be null",
               result != null);
    Persistable file2 = result.getOriginal();
    assertEquals("SingleFile", "Retrieved file should be the same after store",
                 file1, file2);
    assertTrue("SingleFile", "Retrieved file should have no updates",
               result.getUpdates().size() == 0);
    
    // Update file1
    Persistable[] updateArray = {
      new DummyPersistable("update 1"),
      new DummyPersistable("update 2")
    };
    assertTrue("SingleFile", "Update 1 should store successfully",
               sm.update(id1, updateArray[0], userCred));
    assertTrue("SingleFile", "Update 2 should store successfully",
               sm.update(id1, updateArray[1], userCred));
    
    // Retrieve updated file
    result = sm.lookup(id1);
    assertTrue("SingleFile", "Retrieved result should not be null",
               result != null);
    Persistable file3 = result.getOriginal();
    assertEquals("SingleFile", "Retrieved file should be the same after update",
                 file1, file3);
    Vector updates = result.getUpdates();
    for (int i=0; i < updates.size(); i++) {
      Persistable update = (Persistable) updates.elementAt(i);
      assertEquals("SingleFile", "Update " + i + " should be the same",
                   updateArray[i], update);
    }
    
    // Remove file1
    assertTrue("SingleFile", "File should successfully delete",
               sm.delete(id1, userCred));
    result = sm.lookup(id1);
    assertTrue("SingleFile", "Retrieved result should be null",
               result == null);
  }
  
  /**
   * Tests storing multiple files.
   */
  protected void testMultipleFiles() throws TestFailedException {
    StorageManager sm = new StorageManagerImpl(_pm);
    Credentials userCred = new Credentials() {};
    
    // Store file1
    NodeId id1 = _idFactory.generateNodeId();
    Persistable file1 = new DummyPersistable("file 1");
    assertTrue("MultipleFiles", "File 1 should store successfully",
               sm.insert(id1, file1, userCred));
    
    // Store file2
    NodeId id2 = _idFactory.generateNodeId();
    Persistable file2 = new DummyPersistable("file 2");
    assertTrue("MultipleFiles", "File 2 should store successfully",
               sm.insert(id2, file2, userCred));
    
    // Store file3
    NodeId id3 = _idFactory.generateNodeId();
    Persistable file3 = new DummyPersistable("file 3");
    assertTrue("MultipleFiles", "File 3 should store successfully",
               sm.insert(id3, file3, userCred));
    
    // Retrieve file1
    StorageObject result = sm.lookup(id1);
    assertTrue("MultipleFiles", "Retrieved result (1) should not be null",
               result != null);
    Persistable file = result.getOriginal();
    assertEquals("MultipleFiles", "Retrieved file (1) should be the same after store",
                 file1, file);
    assertTrue("MultipleFiles", "Retrieved file (1) should have no updates",
               result.getUpdates().size() == 0);
    
    // Update file2
    Persistable update1 = new DummyPersistable("update 1");
    sm.update(id2, update1, userCred);
    
    // Retrieve file3
    result = sm.lookup(id3);
    assertTrue("MultipleFiles", "Retrieved result (3) should not be null",
               result != null);
    file = result.getOriginal();
    assertEquals("MultipleFiles", "Retrieved file (3) should be the same after store",
                 file3, file);
    assertTrue("MultipleFiles", "Retrieved file (3) should have no updates",
               result.getUpdates().size() == 0);
    
    // Remove file3
    assertTrue("MultipleFiles", "File should successfully delete",
               sm.delete(id3, userCred));
    result = sm.lookup(id3);
    assertTrue("MultipleFiles", "Retrieved result should be null",
               result == null);
    
    
    // Retrieve file2
    result = sm.lookup(id2);
    assertTrue("MultipleFiles", "Retrieved result (2) should not be null",
               result != null);
    file = result.getOriginal();
    assertEquals("MultipleFiles", "Retrieved file (2) should be the same after store",
                 file2, file);
    Vector updates = result.getUpdates();
    assertTrue("MultipleFiles", "Retrieved file (2) should have 1 update",
               updates.size() == 1);
    Persistable update = (Persistable) updates.elementAt(0);
    assertEquals("MultipleFiles", "Update (2) should be the same",
                 update1, update);
  }
  
  /**
   * Tests re-inserting file with different credentials.
   */
  protected void testInsertCredentials() throws TestFailedException {
    StorageManager sm = new StorageManagerImpl(_pm);
    Credentials userCred = new Credentials() { };
    Credentials userCred2 = new Credentials() { };
    
    assertTrue("InsertCredentials", "Credentials should not be equal",
               !userCred.equals(userCred2));
    
    // Store file1 as userCred
    NodeId id1 = _idFactory.generateNodeId();
    Persistable file1 = new DummyPersistable("file 1");
    assertTrue("InsertCredentials", "File should store successfully",
               sm.insert(id1, file1, userCred));
    
    // Store file2 as userCred2
    Persistable file2 = new DummyPersistable("file 2");
    assertTrue("InsertCredentials", "File should not store successfully",
               !sm.insert(id1, file2, userCred2));
    
    // Check correct file exists
    StorageObject result = sm.lookup(id1);
    assertTrue("InsertCredentials", "Retrieved result should not be null",
               result != null);
    Persistable fileA = result.getOriginal();
    assertEquals("InsertCredentials", "Retrieved file should be the first file",
                 file1, fileA);
    
    
    // Store file3 as userCred
    Persistable file3 = new DummyPersistable("file 3");
    assertTrue("InsertCredentials", "File should store successfully",
               sm.insert(id1, file3, userCred));
    
    // Check new file exists
    result = sm.lookup(id1);
    assertTrue("InsertCredentials", "Retrieved result should not be null",
               result != null);
    Persistable fileB = result.getOriginal();
    assertEquals("InsertCredentials", "Retrieved file should be the third file",
                 file3, fileB);
  }
  
  /**
   * Tests deleting file with inappropriate credentials.
   */
  protected void testDeleteCredentials() throws TestFailedException {
    StorageManager sm = new StorageManagerImpl(_pm);
    Credentials userCred = new Credentials() { };
    Credentials userCred2 = new Credentials() { };
    
    assertTrue("DeleteCredentials", "Credentials should not be equal",
               !userCred.equals(userCred2));
    
    // Store file1
    NodeId id1 = _idFactory.generateNodeId();
    Persistable file1 = new DummyPersistable("file 1");
    sm.insert(id1, file1, userCred);
        
    // Remove file1 with wrong credentials
    assertTrue("DeleteCredentials", "File should not successfully delete",
               !sm.delete(id1, userCred2));
    StorageObject result = sm.lookup(id1);
    assertTrue("DeleteCredentials", "Retrieved result should not be null",
               result != null);
    Persistable file2 = result.getOriginal();
    assertEquals("DeleteCredentials", "Retrieved file should be same",
                 file1, file2);
  }
  
  /**
   * Initializes and runs all regression tests.
   */
  public void runTests() {
    initialize();
    
    try {
      // Run each test
      testSingleFile();
      testMultipleFiles();
      testInsertCredentials();
      testDeleteCredentials();

      System.out.println("\n\nDEBUG-All tests passed!---------------------\n");
    }
    catch (TestFailedException e) {
      System.out.println("\n\nDEBUG-Test Failed!--------------------------\n");
      System.out.println(e.toString());
      System.out.println("\n\n--------------------------------------------\n");
    }
  }
  

  /**
   * Usage: StorageRegrTest
   */
  public static void main(String args[]) {

    StorageRegrTest storageTest = new StorageRegrTest();
    storageTest.runTests();

  }


  /**
   * Exception indicating that a regression test failed.
   */
  protected class TestFailedException extends Exception {
    protected TestFailedException(String message) {
      super(message);
    }
  }
}
