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
Basis, without any representations or warranties of any kind, express
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
import java.util.zip.*;

import rice.*;
import rice.Continuation.*;
import rice.p2p.commonapi.*;
import rice.serialization.*;
import rice.selector.*;

/**
 * This class is an implementation of Storage which provides
 * persistent storage to disk.  This class also guarentees that
 * the data will be consistent, even after a crash.  This class
 * also provides these services is a non-blocking fashion by
 * launching a seperate thread which is tasked with actaully
 * writing the data to disk.
 *
 * The serialized objects are stored on-disk in a GZIPed XML format,
 * which provides extensibility with reasonable storage and processing
 * costs.  Additionally, any metadata, if provided, is also stored
 * in the on-disk file.  The format of the file is
 *
 * [Object, Gzipped XML]
 * [Metadata, Gzipped XML]
 * [persistence magic number, long]
 * [persistence version, long]
 * [persistence revision, long]
 * [metadata length, long]
 *
 * The persistence package is set up to automatically upgrade older
 * versions of the on-disk format as new data is written under the
 * key.  
 *
 * Persistence also supports the metadata interface specified in the
 * Catalog interface.  All metadata is guaranteeded to be stored
 * in memory, so fetching the metadata of a given key is an efficient
 * operation.  
 */
public class PersistentStorage implements Storage {
  
  /**
   * Static variables defining the layout of the on-disk storage 
   */
  public static final long PERSISTENCE_MAGIC_NUMBER = 8038844221L;
  public static final long PERSISTENCE_VERSION_2 = 2L;
  public static final long PERSISTENCE_REVISION_2_0 = 0L;
  
  /**
   * Static variables which define the location of the storage root
   */
  public static final String BACKUP_DIRECTORY = "/FreePastry-Storage-Root/";
  public static final String LOST_AND_FOUND_DIRECTORY = "lost+found";
  public static final String METADATA_FILENAME = "metadata.cache";
  public static final String METADATA_FILENAME_EXTENSION = ".metadata";
  
  /**
   * The splitting factor, or the number of files in one directory
   */
  public static final int MAX_FILES = 256;
  
  /**
   * The maximum number of subdirectories in a directory before splitting
   */
  public static final int MAX_DIRECTORIES = 32;
  
  /**
   * The amount of time before re-writing the metadata file
   */
  public static final int METADATA_SYNC_TIME = 300000;
  
  /**
   * Whether or not to print debug output 
   */
  public static final boolean DEBUG = false;
  
  /**
   * The static work queue, which all of the persistence roots use
   */
  public static WorkQueue QUEUE = new WorkQueue();
  
  /**
   * The static worker thread, which services all disk requests for all roots
   */
  public static Thread WORK_THREAD = new PersistenceThread(QUEUE);
  
  /* now, start the worker thread at class-load time */
  static {
    WORK_THREAD.start();
  }

  private IdFactory factory;			  // the factory used for creating ids
  
  private String name;              // the name of this instance
  private File rootDirectory;       // root directory to store stuff in
  private File backupDirectory;     // dir for storing persistent objs
  private File appDirectory;        // dir for storing persistent objs
  private File lostDirectory;       // dir for lost objects
  
  private HashMap directories;      // the in-memory map of directories (for efficiency)
  private HashSet dirty;            // the list of directories which have dirty metadata

  private TreeMap metadata;         // the in-memory cache of object metadata

  private String rootDir;           // rootDirectory

  private long storageSize;         // The amount of storage allowed to be used 
  private long usedSize;            // The amount of storage currently in use
  private IdSet idSet;              // In memory store of all keys
  private Random rng;               // random number generator

 /**
  * Builds a PersistentStorage given a root directory in which to
  * persist the data. Uses a default instance name.
  *
  * @param factory The factory to use for creating Ids.
  * @param rootDir The root directory of the persisted disk.
  * @param size the size of the storage in bytes
  */
  public PersistentStorage(IdFactory factory, String rootDir, int size) throws IOException {
    this(factory, "default", rootDir, size);
  }
   
 /**
  * Builds a PersistentStorage given and an instance name
  *  and a root directoy in which to persist the data. 
  *
  * @param factory The factory to use for creating Ids.
  * @param name the name of this instance
  * @param rootDir The root directory of the persisted disk.
  * @param size the size of the storage in bytes
  */
  public PersistentStorage(IdFactory factory, String name, String rootDir, int size) throws IOException {
    this.factory = factory;
    this.name = name;
    this.rootDir = rootDir;
    this.storageSize = size; 
    this.rng = new Random();
    this.idSet = factory.buildIdSet();
    this.directories = new HashMap();
    this.dirty = new HashSet();
    this.metadata = new TreeMap();
    
    debug("Launching persistent storage in " + rootDir + " with name " + name + " spliting factor " + MAX_FILES);
    
    init();
  } 
  
  /**
   * Method which allows the persistence root to schedle an event which will tell it to 
   * sync the metadata cached. Should be called exactly once after the persistence root is 
   * created.
   *
   * @param timer The timer to use to schedule the events
   */
  public void setTimer(rice.selector.Timer timer) {
    timer.scheduleAtFixedRate(new rice.selector.TimerTask() {
      public void run() {
        QUEUE.enqueue(new WorkRequest(new ListenerContinuation("Enqueue of writeMetadataFile")) {
          public Object doWork() throws Exception {
            writeDirty();
            return Boolean.TRUE;
          }
        });
      }
    }, (new Random(name.hashCode())).nextInt(METADATA_SYNC_TIME), METADATA_SYNC_TIME);
  }
  
  /**
   * Renames the given object to the new id.  This method is potentially faster
   * than store/cache and unstore/uncache.
   *
   * @param oldId The id of the object in question.
   * @param newId The new id of the object in question.
   * @param c The command to run once the operation is complete
   */
  public void rename(final Id oldId, final Id newId, Continuation c) {
    QUEUE.enqueue(new WorkRequest(c) {
      public Object doWork() throws Exception {
        File f = getFile(oldId);
        
        if ((f != null) && (f.exists())) {
          File g = getFile(newId);
          renameFile(f, g);
          
          checkDirectory(g.getParentFile());
          
          synchronized (idSet) {
            idSet.addId(newId); 
            idSet.removeId(oldId); 
          }
          
          return Boolean.TRUE;
        } else {
          return Boolean.FALSE;
        }
      }
    });
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
   * @param metadata The object's metadata
   * @param c The command to run once the operation is complete
   * @return <code>true</code> if the action succeeds, else
   * <code>false</code>.
   */
  public void store(final Id id, final Serializable metadata, final Serializable obj, Continuation c) {
    QUEUE.enqueue(new WorkRequest(c) { 
      public Object doWork() throws Exception {
        /* first, rename the current file to a temporary name */
        File objFile = getFile(id); 
        File transcFile = makeTemporaryFile(id);
        
        /* now, rename the current file to a temporary name (if it exists) */
        renameFile(objFile, transcFile);
        
        /* next, write out the data to a new copy of the original file */
        writeObject(obj, metadata, id, System.currentTimeMillis(), objFile);
        
        /* abort, if this will put us over quota */
        if(getUsedSpace() + getFileLength(objFile) > getStorageSize()) {
          deleteFile(objFile);
          renameFile(transcFile, objFile);
          throw new OutofDiskSpaceException();
        }
        
        System.out.println("COUNT: " + System.currentTimeMillis() + " Storing data of class " + obj.getClass().getName() + " under " + id.toStringFull() + " of size " + objFile.length() + " in " + name);
        
        /* recalculate amount used */
        decreaseUsedSpace(getFileLength(transcFile)); 
        increaseUsedSpace(getFileLength(objFile));
        
        /* now, delete the old file */
        deleteFile(transcFile);
        
        synchronized (idSet) {
          idSet.addId(id); 
          PersistentStorage.this.metadata.put(id, metadata);
          dirty.add(objFile.getParentFile());
        }
        
        /* finally, check to see if this directory needs to be split */
        checkDirectory(objFile.getParentFile());
        
        return Boolean.TRUE;
      }
    });
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
  public void unstore(final Id id, Continuation c) {
    QUEUE.enqueue(new WorkRequest(c) { 
      public Object doWork() throws Exception {
        /* first get the file */
        File objFile = getFile(id); 
        
        System.out.println("COUNT: " + System.currentTimeMillis() + " Unstoring data under " + id.toStringFull() + " of size " + objFile.length() + " in " + name);
        
        /* remove id from stored list */
        synchronized (idSet) { 
          idSet.removeId(id);
          metadata.remove(id);
          dirty.add(objFile.getParentFile());
        }
        
        /* check to make sure file exists */
        if ((objFile == null) || (! objFile.exists()))
          return Boolean.FALSE;
        
        /* record the space collected and delete the file */
        decreaseUsedSpace(objFile.length());
        deleteFile(objFile);
        
        return Boolean.TRUE;
      }
    });
  }

  /**
   * Returns whether or not an object is present in the location <code>id</code>.
   *
   * @param id The id of the object in question.
   * @return Whether or not an object is present at id.
   */
  public boolean exists(Id id) {
    synchronized (idSet) {
      return idSet.isMemberId(id);
    }
  }
  
  /**
   * Returns the metadata associated with the provided object, or null if
   * no metadata exists.  The metadata must be stored in memory, so this 
   * operation is guaranteed to be fast and non-blocking.
   *
   * @param id The id for which the metadata is needed
   * @return The metadata, or null of non exists
   */
  public Serializable getMetadata(Id id) {
    synchronized (idSet) {
      return (Serializable) metadata.get(id);
    }
  }
  
  /**
   * Updates the metadata stored under the given key to be the provided
   * value.  As this may require a disk access, the requestor must
   * also provide a continuation to return the result to.  
   *
   * @param id The id for the metadata 
   * @param metadata The metadata to store
   * @param c The command to run once the operation is complete
   */
  public void setMetadata(final Id id, final Serializable metadata, Continuation c) {
    if (! exists(id)) {
      c.receiveResult(new Boolean(false));
    } else {    
      QUEUE.enqueue(new WorkRequest(c) { 
        public Object doWork() throws Exception {
          /* write the metadata to the file */
          File objFile = getFile(id);
          writeMetadata(objFile, metadata);
          
          System.out.println("COUNT: " + System.currentTimeMillis() + " Updating metadata for " + id.toStringFull() + " in " + name);
          
          /* then update our cache */
          synchronized (idSet) {
            PersistentStorage.this.metadata.put(id, metadata);
            dirty.add(objFile.getParentFile());
          }
          
          return Boolean.TRUE;
        }
      });
    }
  }

  /**
   * Returns the object identified by the given id.
   *
   * @param id The id of the object in question.
   * @param c The command to run once the operation is complete
   * @return The object, or <code>null</code> if there is no cooresponding
   * object (through receiveResult on c).
   */
  public void getObject(final Id id, Continuation c) {
    if (! exists(id)) {
      c.receiveResult(null);
    } else {    
      QUEUE.enqueue(new WorkRequest(c) { 
        public Object doWork() throws Exception {
          try { 
            /* get the file */
            File objFile = getFile(id);
                        
            /* and make sure that it exists */
            if ((objFile == null) || (! objFile.exists())) 
              return null;
            else {
              System.out.println("COUNT: " + System.currentTimeMillis() + " Fetching data under " + id + " of size " + objFile.length() + " in " + name);

              return readData(objFile);
            }
          } catch (Exception e) {
            /* if there's a problem, move the file to the lost+found */
            File objFile = getFile(id);
            moveToLost(objFile);
            
            /* remove our index for this file */
            synchronized (idSet) {
              idSet.removeId(id); 
              metadata.remove(id);
              dirty.add(objFile.getParentFile());
            }
            
            throw e;
          }
        }
      });
    }
  }

  /**
   * Return the objects identified by the given range of ids. The IdSet 
   * returned contains the Ids of the stored objects. The range is
   * partially inclusive, the lower range is inclusive, and the upper
   * exclusive.
   *
   *
   * NOTE: This method blocks so if the behavior of this method changes and
   * uses the disk, this method may be deprecated.
   *
   * @param range The range to query  
   * @return The idset containg the keys 
   */
  public IdSet scan(IdRange range){
    synchronized (idSet) {
      return idSet.subSet(range); 
    }
  }

  /**
   * Return the objects identified by the given range of ids. The IdSet 
   * returned contains the Ids of the stored objects. The range is
   * partially inclusive, the lower range is inclusive, and the upper
   * exclusive.
   *
   * NOTE: This method blocks so if the behavior of this method changes and
   * uses the disk, this method may be deprecated.
   *
   * @return The idset containg the keys 
   */
  public IdSet scan() {
    synchronized (idSet){
      return (IdSet) idSet.clone();
    }
  }
  
  /**
   * Returns a map which contains keys mapping ids to the associated 
   * metadata.  
   *
   * @param range The range to query  
   * @return The map containg the keys 
   */
  public TreeMap scanMetadata(IdRange range) {
    synchronized (idSet) {
      TreeMap map = null;
      
      if (range.getCCWId().compareTo(range.getCWId()) <= 0) {
        map = new TreeMap(metadata.subMap(range.getCCWId(), range.getCWId()));
      } else {
        map = new TreeMap(metadata.tailMap(range.getCCWId()));
        map.putAll(metadata.headMap(range.getCWId()));
      }
      
      return map;
    }
  }
  
  /**
   * Returns a map which contains keys mapping ids to the associated 
   * metadata.  
   *
   * @return The treemap mapping ids to metadata 
   */
  public TreeMap scanMetadata() {
    synchronized (idSet) {
      return new TreeMap(metadata);
    }
  }
  
  /**
   * Returns the total size of the stored data in bytes.The result
   * is returned via the receiveResult method on the provided
   * Continuation with an Integer representing the size.
   *
   * @param c The command to run once the operation is complete
   * @return The total size, in bytes, of data stored.
   */
  public long getTotalSize(){
    return usedSize;
  }
  
  /**
   * Returns the number of Ids currently stored in the catalog
   *
   * @return The number of ids in the catalog
   */
  public int getSize() {
    return idSet.numElements();
  }


  /*****************************************************************/
  /* Functions for init/crash recovery                             */
  /*****************************************************************/

  /**
   * Perform all the miscealanious house keeping that must be done
   * when we start up
   */
  private void init() throws IOException {
    debug("Initing directories");
    initDirectories();
    debug("Initing directory map");
    initDirectoryMap(appDirectory);
    debug("Initing files");
    initFiles(appDirectory);
    debug("Initing file map");
    initFileMap(appDirectory);
    debug("Initing metadata");
    initMetadata();
    debug("Syncing metadata");
    writeDirty();
    debug("Done initing");
  }

  /**
   * Verify that the directory name passed to the
   * PersistenceManagerImpl constructor is valid and
   * creates the necessary subdirectories.
   *
   * @return Whether the directories are successfully initialized.
   */
  private void initDirectories() throws IOException {
    rootDirectory = new File(rootDir);
    createDirectory(rootDirectory);
    
    backupDirectory = new File(rootDirectory, BACKUP_DIRECTORY);
    createDirectory(backupDirectory);
    
    appDirectory = new File(backupDirectory, getName());
    createDirectory(appDirectory);
    
    lostDirectory = new File(backupDirectory, LOST_AND_FOUND_DIRECTORY); 
    createDirectory(lostDirectory);
  }
  
  /**
   * Reads in the in-memory map of directories for use.
   *
   * @param dir The directory to recurse
   */
  private void initDirectoryMap(File dir) {
    File[] files = dir.listFiles(new DirectoryFilter());
    directories.put(dir, files);
    
    for (int i=0; i<files.length; i++) {
      initDirectoryMap(files[i]);
    }
  }
  
  /**
   * Ensures that all files are in the correct directories, and moves files which
   * are in the wrong directory.  Also checks for any temporary files and resolves
   * any detected conflicts.  
   * 
   * @param dir The directory to start on
   */
  private void initFiles(File dir) throws IOException {
    String[] files = dir.list();
    boolean metadata = true;
      
    for (int i=0; i<files.length; i++) {
      /* check each file and recurse into subdirectories */
      if (isFile(dir, files[i])) {
        try {
          files[i] = initTemporaryFile(dir, files[i]);
          
          if (files[i] != null) 
            moveFileToCorrectDirectory(dir, files[i]);
        } catch (Exception e) {
          moveToLost(new File(dir, files[i]));
        }
      } else if (isDirectory(dir, files[i])) {
        initFiles(new File(dir, files[i]));
        metadata = false;
      }
      
      /* release the file for garbage collection */
      files[i] = null;
    }
    
    /* and delete the old metadata file, if it exists */
    if (! metadata)
      deleteFile(new File(dir, METADATA_FILENAME));
  }

  /**
   * Method which initializes a temporary file by doing the following:
   *   1.  If this file is not a temporary file, simply returns the file
   *   2.  If so, sees if the real file exists.  If the real file exists, 
   *       renames this file and returns the new name.
   *   3.  If so, determines which of the two files is the newest, and
   *       deletes the temporary file.  Returns the temporary filename
   *       which doesn't exist any more.
   */
  private String initTemporaryFile(File parent, String name) throws IOException {
    if (! isTemporaryFile(name))
      return name;
    
    File file = new File(parent, name);
    File real = new File(parent, readKey(file).toStringFull());
    
    if (! real.exists()) {
      renameFile(file, real);
      return real.getName();
    }
    
    resolveConflict(file, real, real);
    
    return null;
  }
  
  // DELTE ME
  int count = 0;
  
  /**
   * Inititializes the idSet data structure
   * 
   * In doing this it must resolve conflicts and aborted
   * transactions. After this is run the most current stable
   * state should be restored.  Also record the total used space
   * for all files in the root.
   * Lastly, deletes any files which are of zero length.
   *
   */
  private void initFileMap(File dir) throws IOException {
    /* first, see if this directory needs to be expanded */
    checkDirectory(dir);
    
    /* now, read the metadata file in this directory */
    long modified = readMetadataFile(dir);
    
    /* next, start processing by listing the number of files and going from there */
    File[] files = dir.listFiles();
        
    for (int i=0; i<files.length; i++) {
      if (isFile(dir, files[i].getName())) {
        Id id = readKey(files[i]);
        long len = getFileLength(files[i]);
        
        if (len > 0) {
          increaseUsedSpace(len);
          idSet.addId(id);
        
          /* if the file is newer than the metadata file, update the metadata 
            if we don't have the metadata for this file, update it */
          if ((! metadata.containsKey(id)) || (files[i].lastModified() > modified)) {
            metadata.put(id, readMetadata(files[i]));
            dirty.add(dir);
          }
        } else {
          moveToLost(files[i]);
          
          if (metadata.containsKey(id)) {
            metadata.remove(id);
            dirty.add(dir);
          }
        }
      } else if (files[i].isDirectory()) {
        initFileMap(files[i]);
      }
    }
  }
  
  /**
   * Removes any entries in the metadata file which do not have files
   */
  private void initMetadata() throws IOException {
    Id[] ids = (Id[])  metadata.keySet().toArray(new Id[0]);
    
    for (int i=0; i<ids.length; i++)
      if (! idSet.isMemberId(ids[i])) {
        metadata.remove(ids[i]);
        dirty.add(getDirectoryForId(ids[i]));
      }
  }

  /**
   * Resolves a conflict between the two provided files by picking the
   * newer one and renames it to the given output file.
   *
   * @param file1 The first file
   * @param file2 The second file
   * @param output The file to store the result in
   */
  private void resolveConflict(File file1, File file2, File output) throws IOException {
    if (! file2.exists()) {
      renameFile(file1, output);
    } else if (! file1.exists()) {
      renameFile(file2, output);
    } else if (file1.equals(file2)) {
      renameFile(file1, output);
    } else {
      debug("resolving conflict between " + file1 + " and " + file2);
      
      if (readVersion(file1) < readVersion(file2)) {
        moveToLost(file1);
        renameFile(file2, output);
      } else {
        moveToLost(file2);
        renameFile(file1, output);
      }
    }
  }

  /**
   * Moves a file to the lost and found directory for this instance
   *
   * @param file the file to be moved
   *
   */ 
  private void moveToLost(File file) throws IOException {
    renameFile(file, new File(lostDirectory, file.getName()));
  }
 
  /*****************************************************************/
  /* Helper functions for Directory Splitting Management           */
  /*****************************************************************/
  
  /**
   * Method which checks to see if a file is in the right directory. If not, it
   * places the file into the correct directory;
   *
   * @param file The file to check
   */
  private void checkFile(File file) throws IOException {
    if (! getDirectoryForName(file.getName()).equals(file.getParentFile()))
      moveFileToCorrectDirectory(file);
  }
  
  /**
   * Method which checks to see if a directory has too many files, and if so, 
   * expands the directory in order to bring it to the correct size
   *
   * @param dir The directory to check
   * @return Whether or not the directory was modified
   */
  private boolean checkDirectory(File directory) throws IOException {
    if (numFilesDir(directory) > MAX_FILES) {
      expandDirectory(directory);
      return true;
    }
    
    if (numDirectoriesDir(directory) > MAX_DIRECTORIES) {
      reformatDirectory(directory);
      return true;
    }
    
    return false;
  }
  
  /**
   * This method expands the directory when there are too many subdirectories
   * in one directory.  Basically uses the same logic as expandDirectory(), but also
   * updates the metadata.
   * 
   * This is used to keep less then NUM_FILES in any directory
   * at one time. This speeds up look up time in a particular directory
   * under certain circumstances.
   *
   * @param dir The directory to expand 
   */
  private void reformatDirectory(File dir) throws IOException {
    debug("Expanding directory " + dir + " due to too many subdirectories");
    /* first, determine what directories we should create */
    String[] dirNames = dir.list(new DirectoryFilter());
    String[] newDirNames = getDirectories(dirNames, 0);
    File[] newDirs = new File[newDirNames.length];
    
    /* create the new directories, move the old ones */
    for (int i=0; i<newDirNames.length; i++) {
      newDirs[i] = new File(dir, newDirNames[i]);
      createDirectory(newDirs[i]);
      debug("creating directory " + newDirNames[i]);

      /* now look through the original directory and move any matching dirs */
      String[] subDirNames = getMatchingDirectories(newDirNames[i], dirNames);
      File[] newSubDirs = new File[subDirNames.length];
      
      for (int j=0; j<subDirNames.length; j++) {
        /* move the directory */
        File oldDir = new File(dir, subDirNames[j]);
        newSubDirs[j] = new File(newDirs[i], subDirNames[j].substring(newDirNames[i].length()));
        debug("moving the old direcotry " + oldDir + " to " + newSubDirs[j]);
        renameFile(oldDir, newSubDirs[j]);

        /* remove the stale entry, add the new one */        
        this.directories.remove(oldDir); 
        this.directories.put(newSubDirs[j], new File[0]);
      }
      
      this.directories.put(newDirs[i], newSubDirs);
    }
    
    /* lastly, update the root directory */
    this.directories.put(dir, newDirs);
  } 
  
  /**
   * Returns the sublist of the provided list which starts with the 
   * given prefix.
   *
   * @param prefix The prefix to look for
   * @param list The list to search through
   * @return The sublist
   */
  private String[] getMatchingDirectories(String prefix, String[] dirNames) {
    Vector result = new Vector();
    
    for (int i=0; i<dirNames.length; i++) 
      if (dirNames[i].startsWith(prefix))
        result.add(dirNames[i]);
    
    return (String[]) result.toArray(new String[0]);
  }

  /**
   * This method expands the directory in to a subdirectory
   * for each prefix, contained in the directory
   * 
   * This is used to keep less then NUM_FILES in any directory
   * at one time. This speeds up look up time in a particular directory
   * under certain circumstances.
   *
   * @param dir The directory to expand 
   */
  private void expandDirectory(File dir) throws IOException {
    debug("Expanding directory " + dir + " due to too many files");
    /* first, determine what directories we should create */
    String[] fileNames = dir.list(new FileFilter());
    String[] dirNames = getDirectories(fileNames, getPrefixLength(dir));
    File[] dirs = new File[dirNames.length];
    
    /* create the directories */
    for (int i=0; i<dirNames.length; i++) {
      dirs[i] = new File(dir, dirNames[i]);
      directories.put(dirs[i], new File[0]);
      createDirectory(dirs[i]);
      debug("creating directory " + dirNames[i]);
      
      /* mark this directory for metadata syncing */
      dirty.add(dirs[i]);
    }
    
    directories.put(dir, dirs);
    
    /* last, move the files into the correct directory */
    File[] files = dir.listFiles(new FileFilter());
    for(int i = 0; i < files.length; i++)
      moveFileToCorrectDirectory(files[i]);
    
    /* and remove the metadata file */
    deleteFile(new File(dir, METADATA_FILENAME));
  } 
  
  /**
   * This method takes in a list of the file names in a given directory which
   * needs to be expanded, and returns the lists of directories which should
   * be created for the expansion to happen.
   *
   * @param names The names to return the directories for
   * @return The directory names
   */
  private String[] getDirectories(String[] names, int offset) {
    int length = getPrefixLength(names);
    String prefix = names[0].substring(0, length);
    CharacterHashSet set = new CharacterHashSet();
    
    for (int i=0; i<names.length; i++) 
      set.put(names[i].charAt(length));
    
    char[] splits = set.get();
    String[] result = new String[splits.length];
    
    for (int i=0; i<result.length; i++) 
      result[i] = prefix.substring(offset) + splits[i];
    
    return result;
  }
  
  /**
   * This method takes in the list of file names in a given directory and
   * returns the longest common prefix of all of the names.
   *
   * @param names The names to find the prefix of
   * @return The longest common prefix of all of the names
   */
  private int getPrefixLength(String[] names) {
    int length = names[0].length();
    
    for (int i=0; i<names.length; i++)
      length = getPrefixLength(names[0], names[i], length);
    
    return length;
  }
  
  /**
   * Method which takes in two strings and returns the length of the
   * shared prefix, or a predefined maximum.
   *
   * @param a The first string
   * @param b The second string
   * @param max The maximum value to return
   */
  private int getPrefixLength(String a, String b, int max) {
    int i=0;
    
    for (; (i<a.length()) && (i<b.length()) && (i<max); i++) 
      if (a.charAt(i) != b.charAt(i))
        return i;
    
    return i;
  }
  
  /**
   * Takes a file and moves it to the correct directory
   * this is used in cojunction with expand to move files to their correct
   * subdirectories below the expanded directory
   *
   * @param file The file to be moved
   */
  private void moveFileToCorrectDirectory(File file) throws IOException { 
    moveFileToCorrectDirectory(file.getParentFile(), file.getName());
  }
      
  /**
   * Takes a file and moves it to the correct directory
   * this is used in cojunction with expand to move files to their correct
   * subdirectories below the expanded directory
   *
   * @param file The file to be moved
   */
  private void moveFileToCorrectDirectory(File parent, String name) throws IOException { 
    File real = getDirectoryForName(name);
    
    /* if it's in the wrong directory, then move it and resolve the conflict if necessary */
    if (! real.equals(parent)) {
      //debug("moving file " + file + " to correct directory " + parent);
      File file = new File(parent, name);
      File other = new File(real, file.getName());
      resolveConflict(file, other, other);
      checkDirectory(real);
    }
  }
   
  /*****************************************************************/
  /* Helper functions for File Management                          */
  /*****************************************************************/

  /**
   * Create a directory given its name
   *
   * @param directory The directory to be created
   * @return Whether the directory is successfully created.
   */
  private static void createDirectory(File directory) throws IOException {
    if ((directory != null) && (! directory.exists()) && (! directory.mkdir()))
      throw new IOException("Creation of directory " + directory + " failed!");
  }

  /**
   *
   * Returns the length of a file in bytes
   *
   * @param file file whose length to get
   */
  private static long getFileLength(File file) {
    return (((file != null) && file.exists()) ? file.length() : 0);
  }
  
  /**
   * Renames a given file on disk. 
   *
   * @param oldFile The old name
   * @param newFile The new name
   */
  private static void renameFile(File oldFile, File newFile) throws IOException {
    if ((oldFile != null) && oldFile.exists() && (! oldFile.equals(newFile))) {
      deleteFile(newFile);
        
      if (! oldFile.renameTo(newFile))
        throw new IOException("Rename of " + oldFile + " to " + newFile + " failed!");
    }
  }

  /**
   * deletes a given file from disk
   *
   * @param file file to be deleted.
   *
   */
  private static void deleteFile(File file) throws IOException {
    if ((file != null) && file.exists() && (! file.delete()))
      throw new IOException("Delete of " + file + " failed!");         
  }
  
  /**
   * Returns whether or not the given file is a temporary file.
   * 
   * @param name The name of the file
   * @return Whether or not it is temporary
   */static 
  private boolean isTemporaryFile(String name) {    
    return (name.indexOf(".") >= 0);
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
   * is used to generate the filename, the hashcode is the first try for
   * effeciency.
   */
  private File makeTemporaryFile(Id id) throws IOException {    
    File directory = getDirectoryForId(id);
    File file = new File(directory, id.toStringFull() + "." + rng.nextInt() % 100);
    
    while (file.exists()) 
      file = new File(directory, id.toStringFull() + "." + rng.nextInt() % 100);
    
    return file;
  }
  
  /**
   * Returns whether or not the provided file is an acestor of the other file.  
   *
   * @param file The file to check
   * @param ancestor The potential ancestor
   * @return Whether or not the file is an ancestor
   */
  private boolean isAncestor(File file, File ancestor) {
    while ((file != null) && (! file.equals(ancestor))) {
      file = file.getParentFile();
    }

    return (file != null);
  }

  /**
   * Gets the file for a certain id from the disk
   *
   * @param id the id to get a file for
   * @return File the file for the id
   */
  private File getFile(Id id) throws IOException {
    return new File(getDirectoryForId(id), id.toStringFull());
  }

  /**
   * Gets the directory an id should be stored in
   *
   * @param id the string representation of the id
   * @return File the directory that should contain an id
   */
  private File getDirectoryForId(Id id) throws IOException {
    return getDirectoryForName(id.toStringFull());
  }
  
  /**
   * Gets the directory a given file should be stored in
   *
   * @param name The name of the file
   * @return File the directory that should contain an id
   */
  private File getDirectoryForName(String name) throws IOException {
    return getDirectoryForName(name, appDirectory);
  }
  
  /**
   * Gets the best directory for the given name 
   *
   * @param name The name to serach for
   * @param dir The directory to start at
   * @return The directory the name should be stored in
   */
  private File getDirectoryForName(String name, File dir) throws IOException {
    File[] subDirs = (File[]) directories.get(dir);
    
    if (subDirs.length == 0) {
      return dir;
    } else {
      for (int i=0; i<subDirs.length; i++) 
        if (name.startsWith(subDirs[i].getName())) 
          return getDirectoryForName(name.substring(subDirs[i].getName().length()), subDirs[i]);
      
      /* here, we must create the appropriate directory */
      File newDir = new File(dir, name.substring(0, subDirs[0].getName().length()));
      debug("Necessarily creating dir " + newDir.getName());
      createDirectory(newDir);
      this.directories.put(dir, append(subDirs, newDir));
      this.directories.put(newDir, new File[0]);
      
      /* finally, we must check if this caused too many dirs in one dir.  If so, we
         simply rerun the algorithm which will reflect the new dir */
      if (checkDirectory(dir)) {
        return getDirectoryForName(name, dir);
      } else {
        return newDir;
      }
    }
  }
  
  /**
   * Returns the prefix length of the given directory, or how long the prefix
   * is for all files in the directory.
   *
   * @param dir The directory
   * @return The prefix length
   */
  private int getPrefixLength(File dir) {
    if (dir.equals(appDirectory))
      return 0;
    else 
      return dir.getName().length() + getPrefixLength(dir.getParentFile());
  }
  
  /**
   * Method which the given file to the list of files
   *
   * @param list The list of files
   * @param file The file
   * @return The new list
   */
  private File[] append(File[] files, File file) {
    File[] result = new File[files.length + 1];
    
    for (int i=0; i<files.length; i++) 
      result[i] = files[i];
    
    result[files.length] = file;
    return result;
  }
  
  /**
   * Gets the number of subdirectories in directory
   *
   * @param dir the directory to check
   * @return int the number of directories
   */
  private int numDirectoriesDir(File dir) {
    return dir.listFiles(new DirectoryFilter()).length;
  }
  

  /**
   * Gets the number of files in directory (excluding directories)
   *
   * @param dir the directory to check
   * @return int the number of files
   */
  private int numFilesDir(File dir) {
     return dir.listFiles(new FileFilter()).length;
  }
  
  /**
   * Returns whether or not the given filename represents a stored file
   *
   * @param name The name of the file
   * @return int the number of files
   */
  private boolean isFile(File parent, String name) {
    return (name.length() >= factory.getIdToStringLength());
  }
  
  /**
   * Returns whether or not the given filename represents a directory
   *
   * @param name The name of the file
   * @return int the number of files
   */
  private boolean isDirectory(File parent, String name) {
    return ((name.length() < factory.getIdToStringLength()) && (new File(parent, name)).isDirectory());
  }
  
  /**
   * Returns whether a directory contains subdirectories 
   *
   * @param dir the directory to check
   * @return boolean whether directory contains subdirectories
   *
   */
  private boolean containsDir(File dir) {
    return (dir.listFiles(new DirectoryFilter()).length != 0);
  }
    
  
  /*****************************************************************/
  /* Helper functions for Metadata Storage                         */
  /*****************************************************************/
  
  /**
   * Function which writes out all of the dirty metadata files and marks
   * them as clean.
   */
  protected void writeDirty() {
    File[] files = (File[]) dirty.toArray(new File[0]);
    
    for (int i=0; i<files.length; i++) {
      HashMap map = new HashMap();
      Iterator keys = idSet.subSet(getRangeForDirectory(files[i])).getIterator();
      
      while (keys.hasNext()) {
        Id next = (Id) keys.next();
        map.put(next, metadata.get(next));
      }
      
      try {
        writeMetadataFile(files[i], map);
        
        synchronized (idSet) {
          dirty.remove(files[i]);
        }
      } catch (IOException e) {
        System.err.println("ERROR: Got error " + e + " while writing out metadata file - aborting!");
        e.printStackTrace();
      }
    }
  }

  /**
   * Utility function which reads the metadata file off of disk and stores the
   * result into the memory cache.
   *
   * @param file The directory to read the metadata file for
   */
  private long readMetadataFile(File file) throws IOException {
    File metadata = new File(file, METADATA_FILENAME);
    
    if (! metadata.exists())
      return -1L;

    FileInputStream fin = null;
    
    try {
      fin = new FileInputStream(metadata);
      ObjectInputStream objin = new ObjectInputStream(new BufferedInputStream(fin));
      
      IdRange range = getRangeForDirectory(file);
      
      try {
        HashMap map = (HashMap) objin.readObject();
        Iterator keys = map.keySet().iterator();
        
        while (keys.hasNext()) {
          Id id = (Id) keys.next();
          
          if (range.containsId(id)) 
            this.metadata.put(id, map.get(id));
          else
            dirty.add(file);
        }
        
        return metadata.lastModified();
      } catch (ClassNotFoundException e) {
        System.out.println("ERROR: Got exception " + e + " while reading metadata file " + metadata + " - rebuilding file");
        deleteFile(metadata);
        return 0L;
      } catch (IOException e) {
        System.out.println("ERROR: Got exception " + e + " while reading metadata file " + metadata + " - rebuilding file");
        deleteFile(metadata);
        return 0L;
      }
    } finally {
      fin.close();
    }
  }
  
  /**
   * Utility function which writes the metadata file out to disk.
   *
   * @param file The directory to write the file to
   * @param map The data to write
   */
  private static void writeMetadataFile(File file, HashMap map) throws IOException {    
    FileOutputStream fout = null;
    
    try {
      fout = new FileOutputStream(new File(file, METADATA_FILENAME));
      ObjectOutputStream objout = new ObjectOutputStream(new BufferedOutputStream(fout));
      objout.writeObject(map);
      objout.close();
    } finally {
      fout.close();
    }
  }
  
  /**
   * Utility function which returns the range of keys which a directory 
   * corresponds to.
   *
   * @param dir The directory
   */
  protected IdRange getRangeForDirectory(File dir) {
    String result = "";
    
    while (! dir.equals(appDirectory)) {
      result = dir.getName() + result;
      dir = dir.getParentFile();
    }
        
    return factory.buildIdRangeFromPrefix(result);
  }

  
  /*****************************************************************/
  /* Helper functions for Object Input/Output                      */
  /*****************************************************************/
  
  /**
   * Basic function to read an object from the persisted file.
   *
   * @param file the file to read from
   * @param offset the offset to read from
   * @return Serializable the data stored at the offset in the file
   *
   */
  private static Serializable readObject(File file, int offset) throws IOException {    
    FileInputStream fin = null;
    
    try {
      try {
        fin = new FileInputStream(file);
        ObjectInputStream objin = new XMLObjectInputStream(new BufferedInputStream(new GZIPInputStream(fin)));
        
        /* skip objects */
        for (int i=0; i<offset; i++)
          objin.readObject(); 
        
        return (Serializable) objin.readObject();
      } catch (ClassNotFoundException e) {
        throw new IOException(e.getMessage());
      }
    } finally {
      fin.close();
    }
  }

  /**
   * Abstract over reading a single object to a file using Java
   * serialization.
   *
   * @param file The file to create the object from.
   * @return The object that was read in
   */
  private static Serializable readData(File file) throws IOException {
     return readObject(file, 1);
  }
  
  /**
   * Reads in the metadata from the provided file, or returns null if 
   * no metadata was found
   * 
   * @param file The file which should be read for the metadata
   */
  private static Serializable readMetadata(File file) throws IOException {  
    if (file.length() < 32) 
      return null;
        
    RandomAccessFile ras = null;
    
    try {
      ras = new RandomAccessFile(file, "r");
      ras.seek(file.length() - 32);

      if (ras.readLong() != PERSISTENCE_MAGIC_NUMBER) {
        return null;
      } else if (ras.readLong() != PERSISTENCE_VERSION_2) {
        System.out.println("Persistence version did not match - exiting!");
        return null;
      } else if (ras.readLong() != PERSISTENCE_REVISION_2_0) {
        System.out.println("Persistence revision did not match - exiting!");
        return null;
      }
      
      long length = ras.readLong();
      ras.seek(file.length() - 32 - length);
      
      FileInputStream fis = null;
      
      try {
        fis = new FileInputStream(ras.getFD());
        ObjectInputStream objin = new XMLObjectInputStream(new BufferedInputStream(new GZIPInputStream(fis)));

        try {
          return (Serializable) objin.readObject();
        } catch (ClassNotFoundException e) {
          throw new IOException(e.getMessage());
        }
      } finally {
        fis.close();
      }
    } finally {
      ras.close();
    }
  }

  /**
   * Abstract over reading a single key from a file using Java
   * serialization.
   *
   * @param file The file to create the key from.
   * @return The key that was read in
   */
  private Id readKey(File file) {
    String s = file.getName();

    if (s.indexOf(".") >= 0) {
      return factory.buildIdFromToString(s.toCharArray(), 0, s.indexOf("."));
    } else {
      return factory.buildIdFromToString(s.toCharArray(), 0, s.length());
    }
  }
    
  /**
   * Abstract over reading a version from a file using Java
   * serialization.
   *
   * @param file The file to create the version from.
   * @return The key that was read in
   */
  private static long readVersion(File file) throws IOException {
    Long temp = (Long) readObject(file, 2);
    
    return (temp == null ? 0 : temp.longValue());
  } 

  /**
   * Abstract over writing a single object to a file using Java
   * serialization.
   *
   * @param obj The object to be writen
   * @param file The file to serialize the object to.
   * @return The object's disk space usage
   */
  private static long writeObject(Serializable obj, Serializable metadata, Id key, long version, File file) throws IOException {
    FileOutputStream fout = null;
	  
    try {
      fout = new FileOutputStream(file);
      ObjectOutputStream objout = new XMLObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(fout)));
      objout.writeObject(key);
      objout.writeObject(obj);
      objout.writeObject(new Long(version));
      objout.close();
    } finally {
      fout.close();
    }
    
    long len1 = file.length();
    
    try {
      fout = new FileOutputStream(file, true);
      ObjectOutputStream objout = new XMLObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(fout)));
      objout.writeObject(metadata);
      objout.close();
    } finally {
      fout.close();
    }
    
    long len2 = file.length();
    
    try {
      fout = new FileOutputStream(file, true);
      DataOutputStream dos = new DataOutputStream(fout);
      dos.writeLong(PERSISTENCE_MAGIC_NUMBER);
      dos.writeLong(PERSISTENCE_VERSION_2);
      dos.writeLong(PERSISTENCE_REVISION_2_0);
      dos.writeLong(len2-len1);
      dos.close();
    } finally {
      fout.close();
    }
    
    return file.length();
  }
  
  
  /**
   * Re-writes the metadata stored in the provided file.
   * 
   * @param file The file to which the metadata should be written
   * @param metadata The metadata to write
   */
  private static void writeMetadata(File file, Serializable metadata) throws IOException {
    RandomAccessFile ras = null;
    FileOutputStream fout = null;
    
    try {
      ras = new RandomAccessFile(file, "rw");
      ras.seek(file.length() - 32);
      
      if ((ras.readLong() == PERSISTENCE_MAGIC_NUMBER) && 
          (ras.readLong() == PERSISTENCE_VERSION_2) &&
          (ras.readLong() == PERSISTENCE_REVISION_2_0)) {
        long length = ras.readLong();
        ras.setLength(file.length() - 32 - length);
      } 
    } finally {
      ras.close();
    }
    
    long len1 = file.length();
    
    try {
      fout = new FileOutputStream(file, true);
      ObjectOutputStream objout = new XMLObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(fout)));
      objout.writeObject(metadata);
      objout.close();
    } finally {
      fout.close();
    }
    
    long len2 = file.length();
    
    try {
      fout = new FileOutputStream(file, true);
      DataOutputStream dos = new DataOutputStream(fout);
      dos.writeLong(PERSISTENCE_MAGIC_NUMBER);
      dos.writeLong(PERSISTENCE_VERSION_2);
      dos.writeLong(PERSISTENCE_REVISION_2_0);
      dos.writeLong(len2-len1);
      dos.close();
    } finally {
      fout.close();
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
    if (storageSize > 0)
      return storageSize;
    else
      return Long.MAX_VALUE;
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

  /**
   * 
   * Gets the amound of space currently being used on disk
   *
   * @return long the amount of space being used
   *
   */
  private long getUsedSpace(){
    return usedSize;
  }

  /**
   * Gets the name of this instance
   * 
   * @return String the name of the instance
   *
   */
  public String getName(){
    return name;
  }

  /**
   * Prints out debug statements
   *
   * @param s the string to print out
   */
  private void debug(String s){
    if(DEBUG)
       System.out.println(s);
  }

  /*****************************************************************/
  /* Inner Classes for FileName filtering                          */
  /*****************************************************************/

  /**
   * 
   * A class that filters out the files in a directory to return
   * only subdirectories.
   */ 
  private class DirectoryFilter implements FilenameFilter {
    public boolean accept(File dir, String name) {
      return ((name.length() < factory.getIdToStringLength()) && (new File(dir, name)).isDirectory());
    }
  }
  
  /**
    *
   * A classs that filters out the files in a directory to return
   * only files ( no directories )
   */
  private class FileFilter implements FilenameFilter {
    public boolean accept(File dir, String name) {
      return (name.length() >= factory.getIdToStringLength());
    }
  }
  
  /**
   * Class for a hashtable of primitive characters
   */
  private class CharacterHashSet {
    protected boolean[] bitMap = new boolean[256];
    
    public void put(char a) {
      bitMap[(int) a] = true;
    }
    
    public boolean contains(char a) {
      return bitMap[(int) a];
    }
    
    public void remove(char a) {
      bitMap[(int) a] = false;
    }
    
    public char[] get() {
      int[] nums = getOffsets();
      char[] result = new char[nums.length];
      
      for (int i=0; i<result.length; i++)
        result[i] = (char) nums[i];
      
      return result;
    }
    
    private int[] getOffsets() {
      int[] result = new int[count()];
      int index = 0;
      
      for (int i=0; i<result.length; i++) 
        result[i] = getOffset(i);
      
      return result;
    }
    
    private int getOffset(int index) {
      int location = 0;
      
      /* skip the first index-1 values */
      while (index > 0) {
        if (bitMap[location])
          index--;
        
        location++;
      }
      
      /* find the next true value */
      while (! bitMap[location])
        location++;
        
      return location;
    }
    
    private int count() {
      int total = 0;
      
      for (int i=0; i<bitMap.length; i++)
        if (bitMap[i])
          total++;
      
      return total;
    }
  }
  
  /*****************************************************************/
  /* Inner Classes for Worker Thread                               */
  /*****************************************************************/

  private static class PersistenceThread extends Thread {
    WorkQueue workQ;
	   
	   public PersistenceThread(WorkQueue workQ){
       super("Persistence Worker Thread");
       this.workQ = workQ;
	   }
	   
	   public void run() {
       while (true) 
         workQ.dequeue().run();
	   }
  }
  
  public static class WorkQueue {
    List q = new LinkedList();
	  /* A negative capacity, is equivalent to infinted capacity */
	  int capacity = -1;
	  
	  public WorkQueue() {
	     /* do nothing */
	  }
    
    public synchronized int getLength() {
      return q.size();
    }
	  
	  public WorkQueue(int capacity) {
	     this.capacity = capacity;
	  }
	  
	  public synchronized void enqueue(WorkRequest request) {
      if (capacity < 0 || q.size() < capacity) {
			  q.add(request);
			  notifyAll();
		  } else {
			  request.returnError(new WorkQueueOverflowException());
      }
	  }
	  
	  public synchronized WorkRequest dequeue() {
      while (q.isEmpty()) {
        try {
          wait();
        } catch (InterruptedException e) {
        }
      }
      
      return (WorkRequest) q.remove(0);
    }
	}
  
	private abstract class WorkRequest {
    private Continuation c;
    
		public WorkRequest(Continuation c){
      this.c = c;
		}
		
		public WorkRequest(){
			/* do nothing */
		}
    
    public void returnResult(Object o) {
      c.receiveResult(o); 
    }
    
    public void returnError(Exception e) {
      c.receiveException(e); 
    }
    
    public void run() {
      try {
        final Object result = doWork();
      
        SelectorManager.getSelectorManager().invoke(new Runnable() {
          public void run() {
            returnResult(result);
          }
        });
      } catch (final Exception e) {
        SelectorManager.getSelectorManager().invoke(new Runnable() {
          public void run() {
            returnError(e);
          }
        });
      }
    }
		
		public abstract Object doWork() throws Exception;
	}
	
	
	private static class PersistenceException extends Exception {
	}
	
	private static class OutofDiskSpaceException extends PersistenceException {
	}
	
	private static class WorkQueueOverflowException extends PersistenceException {
	}

}
