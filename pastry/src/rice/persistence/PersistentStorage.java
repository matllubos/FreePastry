package rice.persistence;
/*
 * @(#) PersistenceManager.java
 *
 * @author Ansley Post
 * @author Alan Mislove
 */
import java.io.*;
import java.util.*;

import rice.*;
import rice.pastry.*;

/**
 * This class is an implementation of Storage which provides
 * persistent storage to disk.  This class also guarentees that
 * the data will be consistent, even after a crash.  This class
 * also provides these services is a non-blocking fashion by
 * launching a seperate thread which is tasked with actaully
 * writing the data to disk.
 */
public class PersistentStorage implements Storage {

  private static final String logFileName    = "transactions.persist";
  private static final String serialFileName = "serialTable.persist";

  private File rootDirectory;       // root directory to store stuff in
  private File backupDirectory;     // dir for storing persistent objs
  private File psDirectory;         // dir persistent storage stuff is in
  private File infoDirectory;       // dir for some management info
  private File transDirectory;      // directory for unrolling copies

  private static String rootDir;                          // rootDirectory
  private static final String psDir = "/ps/";             // psDirectory
  private static final String backupDir = "/Backup/";     // backupDirectory
  private static final String infoDir = "/pminfo/";       // infoDirectory
  private static final String transDir = "/transaction";  // transDirectory


  private Hashtable storedObjects;
  private Hashtable persistentObjects;
  private Hashtable cachedObjects;

  /**
   * Builds a PersistentStorage given a root directory in which to
   * persist the data.
   *
   * @param rootDir The root directory of the persisted disk.
   */
  public PersistentStorage(String rootDir) {
    this.rootDir = rootDir;

    if(this.initDirectories())
      System.out.println("Succesfully Initialized Directories");
    else
      System.out.println("ERROR: Failed to Initialized Directories");

    storedObjects = new Hashtable();
    persistentObjects = new Hashtable();
    cachedObjects = new Hashtable();
  }

  /**
   * Makes the object persistent to disk and stored permanantly
   *
   * If the object is already persistent, this method will
   * simply update the object's serialized image.
   *
   * This is implemented atomically so that this may succeed
   * and store the new object, or fail and leave the previous
   * object intact.
   *
   * This method completes by calling recieveResult() of the provided continuation
   * with the success or failure of the operation.
   *
   * @param obj The object to be made persistent.
   * @param id The object's id.
   * @param c The command to run once the operation is complete
   * @return <code>true</code> if the action succeeds, else
   * <code>false</code>.
   */
  public void store(NodeId id, Serializable obj, Continuation c) {
    try {
      if (id == null || obj == null) {
        System.out.println("ERROR: MakePersistent called with null arguments.");
      }

      Serializable storedObj = (Serializable) persistentObjects.get(id);

      /* If we are trying to persist an object twice without changing */
      /* it then we are successful */
      if(storedObj != null && storedObj.equals(obj)){
        //      return true;
      }

      /* If we are trying to persist two different objects with the same */
      /* id then we should fail under non-mutble assumption   */
      if(storedObj != null && !storedObj.equals(obj)){
        System.out.println("ERROR: Tried to Mutate an Object");
      }

      /* Create a file representation and then transactionally write it */
      String fileName = makeFileName(id);
      File objFile = new File(backupDirectory, fileName);
      File transcFile = new File(transDirectory, fileName);

      FileOutputStream fileStream  = new FileOutputStream(transcFile);
      ObjectOutputStream objStream = new ObjectOutputStream(fileStream);

      objStream.writeObject(obj);

      transcFile.renameTo(objFile); /* Assume Atomic */


      storedObjects.put(obj, id);
      persistentObjects.put(obj, id);

      writePersistentTable();
    } catch (Exception e) {
    }
  }

  /**
   * Request to remove the object from the list of persistend objects.
   * Delete the serialized image of the object from stable storage. If
   * necessary. If the object was not in the cached list in the first place,
   * nothing happens and <code>false</code> is returned.
   *
   * This method also guarantees that the data on disk will remain consistent,
   * even after a crash by performing the delete atomically.
   *
   * This method completes by calling recieveResult() of the provided continuation
   * with the success or failure of the operation.
   *
   * @param id The object's persistence id
   * @param c The command to run once the operation is complete
   * @return <code>true</code> if the action succeeds, else
   * <code>false</code>.
   */
  public void unstore(NodeId id, Continuation c) {
  }

  /**
   * Returns whether or not an object is stored in the location <code>id</code>.
   *
   * @param id The id of the object in question.
   * @return Whether or not an object is stored at id.
   */
  public boolean isStored(NodeId id) {
    return false;
  }

  /**
   * Return the object identified by the given id.
   *
   * This method completes by calling recieveResult() of the provided continuation
   * with the result object.
   *
   * @param id The id of the object in question.
   * @param c The command to run once the operation is complete
   * @return The object, or <code>null</code> if the pid is invalid.
   */
  public void getObject(NodeId id, Continuation c) {
  }

  /**
   * Return the nodeIds of objects identified by the given range of ids
   *
   * @param start The staring id of the range.
   * @param end The ending id of the range.
   * @return The nodeIds of objects lying within the given range.
   */
  public NodeId[] getObject(NodeId start, NodeId end) {
    return new NodeId[0];
  }


  /*****************************************************************/
  /* Helper functions for Directory Management                     */
  /*****************************************************************/
  /**
   * Verify that the directory name passed to the
   * PersistenceManagerImpl constructor is valid and
   * creates the necessary subdirectories.
   *
   * @return Whether the directories are successfully initialized.
   */
  private boolean initDirectories()
  {
    rootDirectory = new File(rootDir);
    if (createDir(rootDirectory) == false) {
      return false;
    }

    backupDirectory = new File(rootDirectory, backupDir);
    if (createDir(backupDirectory) == false) {
      return false;
    }

    infoDirectory = new File(rootDirectory, infoDir);
    if (createDir(infoDirectory) == false) {
      return false;
    }

    psDirectory = new File(rootDirectory, psDir);
    if (createDir(psDirectory) == false) {
      return false;
    }
    else {
      File[] files = psDirectory.listFiles();
      int numFiles = files.length;

      for (int i = 0; i < numFiles; i++) {
        files[i].delete();
      }
    }
    transDirectory = new File(rootDirectory, transDir);
    if (createDir(transDirectory) == false) {
      return false;
    }
    return true;
  }

  /**
   * Create a directory given its name
   *
   * @param directory The directory to be created
   * @return Whether the directory is successfully created.
   */
  private boolean createDir(File directory) {
    if (!directory.exists()) {
      directory.mkdir();
    }
    return directory.isDirectory();
  }

  private String makeFileName(NodeId id){
    return id.toString();
  }

  private void writePersistentTable(){

  }

  /*****************************************************************/
  /* Functions for Configuration Management                        */
  /*****************************************************************/


  /**
   * Sets the root directory that the persistence Manager uses
   *
   * @param dir the String representing the directory to use
   * @return boolean, true if the operation suceeds false if it doesn't
   */
  public boolean setRoot(String dir) {
    return true;
  }

  /**
   * gets the root directory that the persistence Manager uses
   *
   * @return String the directory for the root
   */
  public String getRoot() {
    return null;
  }

  /**
   * gets the amount of storage that the persistence Manager uses
   *
   * @return int the amount of storage in MB allocated for use
   */
  public int getStorageSize() {
    return 0;
  }

  /**
   * Sets the amount of storage that the persistence Manager uses
   *
   * @param size the amount of storage available to use in MB
   * @return boolean, true if the operation suceeds false if it doesn't
   */
  public boolean setStorageSize(int size) {
    return true;
  }

  /**
   * gets the amount of storage that the persistence Manager has
   * allocated to be used for storage of persisted object
   *
   * @return int the amount of storage in bytes used for storing objects
   */
  public int getTotalSize() {
    return 0;
  }
}
