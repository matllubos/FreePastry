package rice.persistence; /*
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

  private File rootDirectory;       // root directory to store stuff in
  private File backupDirectory;     // dir for storing persistent objs
  private File transDirectory; // dir for transactions

  private static String rootDir;                          // rootDirectory
  private static final String backupDir = "/Backup/";     // backupDirectory
  private static final String transDir = "/transaction";  // transDirectory

  private long storageSize;
  private long usedSize;
  private Hashtable fileMap;

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
    
   fileMap = new Hashtable();
   initFileMap();

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
  public void store(Comparable id, Serializable obj, Continuation c) {
    try {
      if (id == null || obj == null) {
        System.out.println("ERROR: MakePersistent called with null arguments.");
      }

      if(!fileMap.containsKey(id)){
         /* This is a new entry must create mapping */
         String fileName = makeFileName(id);
         createMapping(id, fileName);
      }

      /* Create a file representation and then transactionally write it */
      File objFile = getFile(id); 
      File transcFile = new File(transDirectory, objFile.getName());

      FileOutputStream fileStream  = new FileOutputStream(transcFile);
      ObjectOutputStream objStream = new ObjectOutputStream(fileStream);
       
      objStream.writeObject(id);
      objStream.writeObject(obj);

      /* I should look at this a little harder */

      decreaseUsedSpace(objFile.length()); /* decrease the amount used */

      transcFile.renameTo(objFile); /* Assume Atomic */

      /* maybe i should combine this increase and decrease */
  
      increaseUsedSpace(objFile.length()); /* increase the amount used */
      

      c.receiveResult(new Boolean(true));

    } catch (Exception e) {
       e.printStackTrace();
       c.receiveException(e);
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
  public void unstore(Comparable id, Continuation c) {
     /* 1. Should remove this from fileMap*/
     /* 3. Should remove from disk */
     /* 4. Should update the value of used space */

      File objFile = getFile(id); 
      removeMapping(id);
      decreaseUsedSpace(objFile.length());
      objFile.delete();
       
      c.receiveResult(new Boolean(true));
  }

  /**
   * Returns whether or not an object is present in the location <code>id</code>.
   * The result is returned via the receiveResult method on the provided
   * Continuation with an Boolean represnting the result.
   *
   * @param c The command to run once the operation is complete
   * @param id The id of the object in question.
   * @return Whether or not an object is present at id.
   */
  public void exists(Comparable id, Continuation c) {
     c.receiveResult(new Boolean(fileMap.containsKey(id))); 
  }

  /**
   * Returns the object identified by the given id.
   *
   * @param id The id of the object in question.
   * @param c The command to run once the operation is complete
   * @return The object, or <code>null</code> if there is no cooresponding
   * object (through receiveResult on c).
   */
  public void getObject(Comparable id, Continuation c){
      File objFile = getFile(id); 
      c.receiveResult(readObject(objFile));
  }

  /**
   * Return the objects identified by the given range of ids. The array
   * returned contains the Comparable ids of the stored objects. The range is
   * completely inclusive, such that if the range is (A,B), objects with
   * ids of both A and B would be returned.
   *
   * Note that the two Comparable objects should be of the same class
   * (otherwise no range can be created).
   *
   * When the operation is complete, the receiveResult() method is called
   * on the provided continuation with a Comparable[] result containing the
   * resulting IDs.
   *
   * @param start The staring id of the range.
   * @param end The ending id of the range.
   * @param c The command to run once the operation is complete
   * @return The objects
   */
  public void scan(Comparable start, Comparable end, Continuation c) {
    try {
      start.compareTo(end);
      end.compareTo(start);
    } catch (ClassCastException e) {
        c.receiveException(new IllegalArgumentException("start and end passed into scan are not co-comparable!"));
        return;
    }

    Vector result = new Vector();
    Iterator i = fileMap.keySet().iterator();

    while (i.hasNext()) {
      try {
        Comparable thisID = (Comparable) i.next();
        if ((start.compareTo(thisID) <= 0) &&
            (end.compareTo(thisID) >= 0))
          result.addElement(thisID);
      } catch (ClassCastException e) {
     }
    }

    Comparable[] array = new Comparable[result.size()];

    for (int j=0; j<result.size(); j++) {
      array[j] = (Comparable) result.elementAt(j);
    }

    c.receiveResult(array);
   }

  /**
   * Returns the total size of the stored data in bytes.The result
   * is returned via the receiveResult method on the provided
   * Continuation with an Integer representing the size.
   *
   * @param c The command to run once the operation is complete
   * @return The total size, in bytes, of data stored.
   */
  public void getTotalSize(Continuation c){
    c.receiveResult(new Long(usedSize));
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

    transDirectory = new File(rootDirectory, transDir);
    if (createDir(transDirectory) == false) {
      return false;
    }
    else{
      File[] files = transDirectory.listFiles();
      int numFiles = files.length;
      for ( int i = 0; i < numFiles; i++){
        files[i].delete(); /* clean out all uncompleted transactions */ 
      }
    }
     
    return true;
  }

  private void initFileMap(){

    File[] files = backupDirectory.listFiles();
    int numFiles = files.length;

    for ( int i = 0; i < numFiles; i++){
       /* insert keys into file Map */
       fileMap.put(readKey(files[i]), files[i]);
       increaseUsedSpace(files[i].length()); /* increase the amount used */
    }
  
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

  private String makeFileName(Comparable id){
    /* I'm using the hashCode, hopefully this won't collide ... */
    return String.valueOf(id.hashCode());
  }

  /*****************************************************************/
  /* Helper functions for Managing/Using FileMap                   */
  /*****************************************************************/
  private File getFile(Comparable id){
     return (File) fileMap.get(id);
  }
  
  private void createMapping(Comparable id, String fileName){
       File objFile = new File(backupDirectory, fileName);
     synchronized(fileMap){
       /* create a file */
       fileMap.put(id, objFile);
     }
  }
  
  private void removeMapping(Comparable id){
     synchronized(fileMap){
       fileMap.remove(id);
     }
  }

  /*****************************************************************/
  /* Helper functions for Object Input/Output                      */
  /*****************************************************************/

  /**
   * Abstract over reading a single object to a file using Java
   * serialization.
   *
   * @param file The file to create the object from.
   * @return The object that was read in
   */
  public static Serializable readObject(File file){
    Serializable toReturn = null;
    if(file == null)
       return null;
    if(!file.exists())
       return null;

    FileInputStream fin;
    ObjectInputStream objin;

    synchronized (file) {
          try {
              fin = new FileInputStream(file);
              objin = new ObjectInputStream(fin);
              objin.readObject(); /* skip key */
              toReturn = (Serializable) objin.readObject();
              fin.close();
              objin.close();
          }
          catch (Exception e) {
             e.printStackTrace();
          }
    }
    return toReturn;
  }

  /**
   * Abstract over reading a single key from a file using Java
   * serialization.
   *
   * @param file The file to create the key from.
   * @return The key that was read in
   */
  public static Serializable readKey(File file){
    Serializable toReturn = null;
    if(file == null)
       return null;
    if(!file.exists())
       return null;

    FileInputStream fin;
    ObjectInputStream objin;

    synchronized (file) {
          try {
              fin = new FileInputStream(file);
              objin = new ObjectInputStream(fin);
              toReturn = (Serializable) objin.readObject();
              fin.close();
              objin.close();
          }
          catch (Exception e) {
             e.printStackTrace();
          }
    }
    return toReturn;
  }
     
     

  /**
   * Abstract over writing a single object to a file using Java
   * serialization.
   *
   * @param obj The object to be writen
   * @param file The file to serialize the object to.
   * @return The object's disk space usage
   */
   public static long writeObject(Object obj, File file) {
       if (obj == null || file == null)
            return 0;

       FileOutputStream fout;
       ObjectOutputStream objout;

       synchronized (file) {
          try {
              fout = new FileOutputStream(file);
              objout = new ObjectOutputStream(fout);
              objout.writeObject(obj);
              fout.close();
              objout.close();
          }
          catch (Exception e) {
             e.printStackTrace();
          }
          return file.length();
       }
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
    /* We should do something logical here to the existing files */
    rootDir = dir;
    return true;
  }

  /**
   * gets the root directory that the persistence Manager uses
   *
   * @return String the directory for the root
   */
  public String getRoot() {
    return rootDir;
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
    if(storageSize <= size){
        storageSize = size;
        return true;
    }
    else if( size > usedSize){
        storageSize = size;
        return true;
    }
    else {
       return false;
    }

  }
  
  /**
   * 
   * Increases the amount of storage recorded as used 
   *
   * @param long i the amount to increase usage by 
   */
  private void increaseUsedSpace(long i){
     usedSize = usedSize + i;
  }

  /**
   * 
   * decreases the amount of storage recorded as used 
   *
   * @param long i the amount to decrease usage by 
   */
  private void decreaseUsedSpace(long i){
     usedSize = usedSize - i;
  }
}
