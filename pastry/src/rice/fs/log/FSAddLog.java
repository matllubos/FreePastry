package rice.fs.log;

import rice.*;
import rice.pastry.*;
import rice.post.*;
import rice.post.storage.*;
import rice.post.log.*;
import rice.fs.*;


/**
 * This class represents a log entry that adds to the file system
 *
 */
public class FSAddLog extends LogEntry{
   
    /* A reference to the data that has been added */
    private ContentHashReference handle = null;
 
    /* The name of the content that has been added */
    private String name = null;
 
    /* A flag for if something is a directory or not. */
    private boolean isDirectory = false;

    /**
     * Constructor
     *
     * @param  handle the handle to the data added
     * @param  name the name associated with the data stored
     */
    public FSAddLog(ContentHashReference handle, String name){
        this(handle, name, false);
    } 

    /**
     * Constructor
     *
     * @param  handle the handle to the data added
     * @param  name the name associated with the data stored
     * @param  isDirectory if the file stored is a directory
     */
    public FSAddLog(ContentHashReference handle, String name, boolean isDirectory){

     this.handle = handle;
      this.name = name;
     this.isDirectory = isDirectory;
    } 

    /**
     * Gets the reference to the data stored 
     *
     * @return the reference
     *
     */
    public ContentHashReference getReference(){
      return this.handle;
    }
 
    /**
     * Gets the name of the data stored 
     *
     * @return the file name
     *
     */
    public String getFileName(){
      return this.name;
    }
 
    /**
     * Gets the whether this entry is for a dir
     *
     * @return directory flag 
     *
     */
    public boolean isDirectory(){
      return this.isDirectory;
    }
}
