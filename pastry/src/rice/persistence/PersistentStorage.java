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

package rice.persistence; 

/*
 * @(#) PersistenceManager.java
 *
 * @author Ansley Post
 * @author Alan Mislove
 *
 * @version $Id$
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

  private static String rootDir;                          // rootDirectory
  private static final String backupDir = "/FreePastry-Storage-Root/"; // backupDirectory

  private long storageSize; /* The amount of storage allowed to be used */
  private long usedSize; /* The amount of storage currently in use */
  private Hashtable fileMap; /* Mapds Id -> Files */
  /**
   * Builds a PersistentStorage given a root directory in which to
   * persist the data.
   *
   * @param rootDir The root directory of the persisted disk.
   */
  public PersistentStorage(String rootDir, int size) {
    this.rootDir = rootDir;

    if(this.initDirectories())
      System.out.println("Succesfully Initialized Directories");
    else
      System.out.println("ERROR: Failed to Initialized Directories");
   storageSize = size; 
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
        c.receiveResult(new Boolean(false));
        return;
      }

      /* Create a file representation and then transactionally write it */
      File objFile = getFile(id); 
      /* change the name here */
      File transcFile = makeFile(id);
      writeObject(obj, id, readVersion(objFile) + 1, transcFile);


      if( getUsedSpace() + getFileLength(objFile) > getStorageSize()){
         /* abort, this will put us over quota */
         deleteFile(transcFile); 
         c.receiveResult(new Boolean(false));
         return;
      }
      else{
        /* complete transaction */
        decreaseUsedSpace(getFileLength(objFile)); /* decrease amount used */
        deleteFile(objFile);
        increaseUsedSpace(transcFile.length()); /* increase the amount used */
        createMapping(id, transcFile);
       } 

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

      File objFile = getFile(id); 
      if(objFile == null){
       c.receiveResult(new Boolean(false));
       return;
      } 
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
      Object toReturn = null;
      try{ 
        toReturn = readData(objFile);
      }
      catch(Exception e){
        e.printStackTrace();
      }
      c.receiveResult(toReturn);
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
   */ private boolean initDirectories()
  {
    rootDirectory = new File(rootDir);
    if (createDir(rootDirectory) == false) {
      return false;
    }

    backupDirectory = new File(rootDirectory, backupDir);
    if (createDir(backupDirectory) == false) {
      return false;
    }

     
    return true;
  }


  /**
   * Inititializes the FileMap data structure
   * 
   * In doing this it must resolve conflicts and aborted
   * transactions. After this is run the most current stable
   * state should be restored
   *
   */
  private void initFileMap(){

    File[] files = backupDirectory.listFiles();
    int numFiles = files.length;

    for ( int i = 0; i < numFiles; i++){
       /* insert keys into file Map */
       /* need to check for uncompleted and conflicting transactions */
       if(files[i].isFile()){

          try{
             
            long version = readVersion(files[i]);
            Object key = readKey(files[i]);

            if(!fileMap.containsKey(key)){
              fileMap.put(key , files[i]);
              increaseUsedSpace(files[i].length()); /* increase amount used */
            }
            else{
               /* resolve conflict due to unfinished trans */
              fileMap.put(key, resolveConflict((File) fileMap.get(key), files[i]));
              System.out.println("Resolving Conflicting Versions");
            }
          }
          catch(java.io.EOFException e){
              System.out.println("Recovering From Incomplete Write");
              files[i].delete();
          }
          catch(java.io.IOException e){
            System.out.println("Caught File Exception");
            /* handle the case where there is an uncompleted trans */
          }
          catch(Exception e){
            System.out.println("Caught OTHER EXCEPTION");
          }
       }
    }
  
  }

  /**
   * resolve a Conflict between two files that claim to have
   * the same key 
   *
   * Checks the version number and returns the newest one
   * and adjust acounting information correctly
   *
   * @param oldFile the File already mapped
   * @param newFile the conflicting file
   *
   * @return File the correct file
   *
   */
  private File resolveConflict(File oldFile, File newFile) throws Exception{
     if(readVersion(oldFile) > readVersion(newFile)){
        newFile.delete();
        return oldFile;
     }
     else{
        decreaseUsedSpace(oldFile.length());
        oldFile.delete();
        increaseUsedSpace(newFile.length());
        return newFile;
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

  private long getFileLength(File file){
   if (file == null)
      return 0;
   else 
      return file.length();
  }

  private void deleteFile(File file){
    if(file != null)
       file.delete();
  }
  /**
   * Generates a new file name to assign for a given id
   *
   * @param Comparable id the id to generate a name for
   * @return String the new File name
   *
   * This method will return the hashcode of the object used as the id
   * unless there is a collision, in which case it will return a random number
   * Since this mapping is only needed once it does not matter what number
   * is used to generate the filename, the hashcode is the first try fo
   * effeciency.
   */
  private File makeFile(Comparable id){
    File file = new File(backupDirectory, String.valueOf(id.hashCode()));
    while(fileMap.contains(file)){
      file = new File(backupDirectory, String.valueOf(new Random().nextInt()));
    }
    return file;
  }

  /*****************************************************************/
  /* Helper functions for Managing/Using FileMap                   */
  /*****************************************************************/
  private File getFile(Comparable id){
     return (File) fileMap.get(id);
  }
  
  private void createMapping(Comparable id, File file){
     synchronized(fileMap){
       /* create a file */
       fileMap.put(id, file);
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

  public static Serializable readObject(File file , int offset) throws Exception  {

    Serializable toReturn = null;
    if(file == null)
       return null;
    if(!file.exists())
       return null;

    FileInputStream fin;
    ObjectInputStream objin;
    synchronized (file) {
        fin = new FileInputStream(file);
        objin = new ObjectInputStream(fin);
        for(int i = 0 ; i < offset; i ++){
           objin.readObject(); /* skip objects */
        }
        toReturn = (Serializable) objin.readObject();
        fin.close();
        objin.close();
    }
    return toReturn;
  }


  /**
   * Abstract over reading a single object to a file using Java
   * serialization.
   *
   * @param file The file to create the object from.
   * @return The object that was read in
   */
  public static Serializable readData(File file) throws Exception{
     return(readObject(file, 1));
  }



  /**
   * Abstract over reading a single key from a file using Java
   * serialization.
   *
   * @param file The file to create the key from.
   * @return The key that was read in
   */
  public static Serializable readKey(File file) throws Exception{
    return (readObject(file, 0));
  }
     
    
  /**
   * Abstract over reading a version from a file using Java
   * serialization.
   *
   * @param file The file to create the version from.
   * @return The key that was read in
   */
  public static long readVersion(File file) throws Exception{
   long toReturn = 0;
   Long temp = ((Long) readObject(file, 2));
   if(temp != null){
     toReturn = temp.longValue();
   }
   return (toReturn);
  } 

  /**
   * Abstract over writing a single object to a file using Java
   * serialization.
   *
   * @param obj The object to be writen
   * @param file The file to serialize the object to.
   * @return The object's disk space usage
   */
   public static long writeObject(Serializable obj, Comparable key, long version, File file)     {
       if (obj == null || file == null)
            return 0;

       FileOutputStream fout;
       ObjectOutputStream objout;

       synchronized (file) {
          try {
              fout = new FileOutputStream(file);
              objout = new ObjectOutputStream(fout);
              objout.writeObject(key);
              objout.writeObject(obj);
              objout.writeObject(new Long(version));
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
  public long getStorageSize() {
    return storageSize;
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

  private long getUsedSpace(){
    return usedSize;
  }
}
