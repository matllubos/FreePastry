package rice.persistence;
/*
 * @(#) PersistenceManager.java
 *
 * @author Ansley Post
 */
import java.io.*;
import java.util.*;
import rice.pastry.*;

public class PersistenceManagerImpl implements PersistenceManager{

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
 
    public PersistenceManagerImpl(String rootDir)
    {
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
     * object in tact.
     * 
     * @param obj The object to be made persistent.
     * @param pid The object's id.
     * @return <code>true</code> if the action succeeds, else
     * <code>false</code>.
     */
   public boolean persist(java.io.Serializable obj, NodeId id){

      if (id == null || obj == null) {
        System.out.println("ERROR: MakePersistent called with null arguments.");
        return false;
      }
      
      java.io.Serializable storedObj = persistentObjects.get(id);
      
      /* If we are trying to persist an object twice without changing */
      /* it then we are successful */
      if(storedObj != null && storedObj.equals(obj)){
        return true;
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

      return true;
   }

    /**
     * Request to remove the object from the list of persistend objects.
     * Delete the serialized image of the object from stable storage. If
     * necessary.
     *
     * <p> If the object was not in the cached list in the first place,
     * nothing happens and <code>false</code> is returned.
     *
     * @param pid The object's persistence id
     * @return <code>true</code> if the action succeeds, else
     * <code>false</code>.
     */
   public boolean unpersist(NodeId id){
     return false;
   }



    /**
     * Caches the object for potential future use
     * 
     * If the object is already persistent, this method will
     * simply update the object's serialized image.
     *
     * This is implemented atomically so that this may succeed
     * and store the new object, or fail and leave the previous
     * object in tact.
     *
     * @param obj The object to be made persistent.
     * @param id The object's id.
     * @return <code>true</code> if the action succeeds, else
     * <code>false</code>.
     */
   public boolean cache(java.io.Serializable obj, NodeId id){
      storedObjects.put(obj, id);	
      cachedObjects.put(obj, id);	
      return true;
   }

    /**
     * Request to remove the object from the list of cached objects.
     * Delete the serialized image of the object from stable storage. If
     * necessary.
     *
     * <p> If the object was not in the cached list in the first place,
     * nothing happens and <code>false</code> is returned.
     *
     * @param pid The object's persistence id
     * @return <code>true</code> if the action succeeds, else
     * <code>false</code>.
     */
   public boolean uncache(NodeId id){
      if(cachedObjects.get(id) == null){
        return false;
      }
      return true;
   }

   /**
    * Return the object identified by the given id.
    *
    * @param id The id of the object in question.
    * @return The object, or <code>null</code> if the pid is invalid.
    */
    public java.io.Serializable getObject(NodeId id){
	return (java.io.Serializable) storedObjects.get(id);
    }

   /**
    * Return the objects identified by the given range of ids
    *
    * @param start The staring id of the range.
    * @param end The ending id of the range.
    * @return The objects, or <code>null</code> if there are no objects in
    *  range .
    */
    public Vector getObject(NodeId start, NodeId end){
      return null;
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
   public boolean setRoot(String dir){
   }

  /**
   * gets the root directory that the persistence Manager uses 
   *
   * @return String the directory for the root 
   */
   public String getRoot(){
   }

  /**
   * gets the amount of storage that the persistence Manager uses 
   *
   * @return int the amount of storage in MB allocated for use 
   */
   public int getStorageSize(){
   }

  /**
   * Sets the amount of storage that the persistence Manager uses 
   *
   * @param size the amount of storage available to use in MB
   * @return boolean, true if the operation suceeds false if it doesn't 
   */
   public boolean setStorageSize(int size){
   }

  /**
   * gets the amount of storage that the persistence Manager has
   * allocated to be used for storage of persisted object 
   *
   * @return int the amount of storage in MB used for storing objects
   */
   public int getUsedStorageSize(){
   }
}
