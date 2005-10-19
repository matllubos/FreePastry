package rice.fs.proxy;

import rice.p2p.commonapi.IdFactory;

import rice.pastry.client.*;
import rice.pastry.commonapi.*;
import rice.pastry.leafset.*;
import rice.pastry.security.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.wire.*;
import rice.pastry.standard.*;

import rice.fs.*;

import rice.scribe.*;
import rice.scribe.messaging.*;

import rice.p2p.past.*;

import rice.persistence.*;

import rice.post.*;
import rice.post.security.*;
import rice.post.proxy.*;

import rice.testharness.*;
import rice.testharness.messaging.*;

import java.util.*;
import java.io.*;
import java.net.*;
import java.security.*;



/**
* This class starts up everything on the Pastry side, and then
* boots up the PAST, Scribe, POST, and FS services.
*/
public class FSProxy extends PostProxy{

  /* A vector storing all the files I already know about, */
  /* probably not the most effecient way of doing things  */
  private Vector existingFiles = new Vector();

  /* The FSService the proxy uses to do things in the network */
  private FSService fsservice  = null;

  private String directoryString;
  
  /* The directory in which the system is rooted from */
  private File directory;

  private boolean shutdown = false;
 
  private boolean interactive;
  
  /**
   * Constructor
   *
   */
  public FSProxy(String[] args) {
    super(args);

    if (directoryString == null) {
      directoryString = ".";
    }

    this.directory = new File(directoryString);
  }

  /**
   * Method that starts up the system
   *
   */
  public void start(){
    super.start();
    try {
    
      sectionStart("Starting FS service");
      stepStart("Starting FSService");
      fsservice = new FSService(post, post.getEntityAddress(), this);
      stepDone(SUCCESS);
      Thread.sleep(1000);
      initFiles(directory);
      fsservice.init();      
      stepStart("Initializing File List");
      stepDone(SUCCESS);
      if(interactive){
          CommandThread ct = new CommandThread(this);
          ct.start();
      }
      while (!shutdown) {
        Thread.sleep(5000);
        post.announcePresence();
        checkForNewFiles(directory);
      }
      System.exit(0);

    } catch (Exception e) {
      System.out.println("Exception occured during construction " + e + " " + e.getMessage());
      e.printStackTrace();
    }

  }
  
  protected void shutdown(){
     System.out.println("The system is shutting down, this may take a few seconds....");
     shutdown = true;
  }

  /**
   * Initialies the system locally, right now only creates the root
   * if it doesn't exist
   *
   */
  public void initFiles(File dir){
     if(!(dir.exists())){
        dir.mkdir();
     }
  }

  /**
   * Runs periodically to check and see if new files have 
   * been added locally.
   *
   * Then the files are stored in the remote store
   *
   * @param dir the directory to check for additions
   *
   */
  public void checkForNewFiles(File dir){
    //System.out.println("Checking for new files");
    NewFilter nff = new NewFilter(existingFiles);
    DirectoryFilter dff = new DirectoryFilter();
    String [] files  = dir.list(nff);
     if(files != null){
       for(int i = 0 ; i < files.length; i ++){
          System.out.println("Found File " + getCanonicalName(new File(dir, files[i])));
          existingFiles.addElement(getCanonicalName(new File(dir, files[i])));
          System.out.println("Adding " + getCanonicalName(new File(dir, files[i])));
          File newFile = new File(dir, files[i]);
          if(newFile.isFile()){
             fsservice.storeFile(new File(dir, files[i]));
          }
          else if(newFile.isDirectory()){
             fsservice.createDir(new File(dir, files[i]));
          }
          else{
             System.out.println("Unexpected Error, not file but not dir???");
          }
       }
     }
     files = dir.list(dff);
     if(files != null){
       for(int i = 0 ; i < files.length; i ++){
         checkForNewFiles(new File(dir, files[i])); 
       }
     }
  }


    
  /**
   * The main method, parses args than creates an instance
   *
   * @param args you know what this is
   *
   */    
  public static void main(String[] args) {
    FSProxy proxy = new FSProxy(args);
    proxy.start();
  }

  protected void parseArgs(String[] args) {
    super.parseArgs(args);

    for (int i = 0; i < args.length - 1; i++) {
      if(args[i].equals("-dir")){
        directoryString = args[i+1];
        break;
      }
    }

    for (int i = 0; i < args.length; i++) {
      if(args[i].equals("-interactive")){
        interactive = true;
        break;
      }
    }
  }

  /**
   * Upcall Method 
   *
   * Called when the services needs to store data on the local
   * system.
   *
   * @param file the name of the file
   * @param data the actual content to be stored
   */
  public void storeData(String file, byte[] data){
     //System.out.println("Storing Data");
     if(existingFiles.contains(file)){
       //System.out.println("I already know about that file");
     }
    else{
       try{
       System.out.println("Storing " + file);
       File outFile = new File(directory, file);
       outFile.createNewFile();
       FileOutputStream fos = new FileOutputStream(outFile);
       fos.write(data);
       existingFiles.addElement(getCanonicalName(new File(directory, file)));
       }
       catch(IOException ioe){
           ioe.printStackTrace();
       }
    } 
  }

  /**
   * Upcall Method
   *
   * Called when the service needs to check if a file already exists
   *
   */ 
  public boolean existsLocally(String file){
    return ( (new File(directory, file)).exists() ); 
  }

  /**
   * Upcall Method
   *
   * Called when the service wants to notify the proxy about a file
   *
   */
  public void registerFile(String s){
       existingFiles.addElement(getCanonicalName(new File(directory, s)));
  }

  /**
   * Upcall Method
   *
   * Used by the service to request the creation of a dir
   *
   * @param file the name of the dir
   *
   */
  public void createLocalDir(String file){
        System.out.println("Creating Dir" + file);
        if(existingFiles.contains(file)){
             
        }
        else{
          existingFiles.addElement(getCanonicalName(new File(directory, file)));
          (new File(directory, file)).mkdir(); 
        }
  }
  /**
   * This class filters files that are new from the list of files that are
   * existing
   *
   */
  private class NewFilter implements FilenameFilter{
        private Vector existing = null;
        public NewFilter(Vector existing){
           this.existing = existing;
        }
        public boolean accept(File file, String fileName){
            /* should be using Canonical Name */
            if(existing.contains(getCanonicalName(new File(file, fileName)))) return false;
            else return true; 
        }
  } 

  private class DirectoryFilter implements FilenameFilter{
        public boolean accept(File file, String fileName){
            if(file.isDirectory())
               return true;
            else
               return false;
         }
  }

  /**
   * Used to get a canonical name for a file
   *
   * the convention being used is the path of the file from
   * below the root of the system
   */
  public String getCanonicalName(File f){
    /* in need the part of the path that isn't part of the root */
   
    String toReturn = f.getAbsolutePath().substring(directory.getAbsolutePath().length() + 1) ;
    return toReturn;
  }
 
  private class CommandThread extends Thread{
       BufferedReader input = null;
       FSProxy proxy = null;
       public CommandThread(FSProxy proxy){
         this.input = new BufferedReader(new InputStreamReader(System.in));
         this.proxy = proxy;
       }
       public void run(){
          boolean quit = false;
          while(!quit){
             System.out.print("Please Enter a command: ");
             try{
               String command = input.readLine();
               System.out.println("You entered " + command);
               if(command.trim().equals("quit")){
                  proxy.shutdown();
                  quit = true;
               }
             }
             catch( IOException e){
              e.printStackTrace();
             }
          }
       }
  }
}
