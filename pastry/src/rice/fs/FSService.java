package rice.fs;

import java.util.*;
import rice.pastry.Id;
import rice.fs.proxy.*;
import rice.fs.log.*;
import rice.*;
import rice.Continuation.*;
import rice.post.*;
import rice.post.log.*;
import rice.post.messaging.*;
import rice.post.storage.*;
import rice.post.security.*;
import rice.email.log.*;
import rice.email.messaging.*;
import java.io.*;


/**
 * This class serves as the client for the FSapplication that sits 
 * on top of Post.<br>
 * <br>
 * 
 */

public class FSService extends PostClient {
    
    // the FS Service's Post object
    public Post post;
    
    
    // the log representing the files
    protected Log log;
    
    // The post user address of the client that uses this service
    public final PostUserAddress pua;

    private FSProxy proxy;

    /**
     * Constructor
     *
     *@param post The Post service to use
     *@param pua the post user
     *@param proxy the proxy to communicate with
     */
    public FSService(Post post, final PostEntityAddress pua, FSProxy proxy) {
	this.post = post;
	post.addClient(this);
        post.joinGroup(new PostGroupAddress("abpost"), null);
        this.proxy = proxy;
	this.pua = (PostUserAddress) pua;
        Continuation store = new Continuation(){
               public void receiveResult(Object o){
                 log = (Log) o;
               }
               public void receiveException(Exception e) {
                   e.printStackTrace();
               }
          };
        post.getPostLog(store);
    }
    
    /**
     *@return the post object this serivce is using.
     */
    public Post getPost() {
	return post;
    }
   

    /**
     * Initializes the system, check to see if all the files
     * that should be there are in fact there
     * fetches the log to do this.
     *
     */
    public void init(){
          initDir();
    }

    private void initFiles(Log l){
       LogEntry logentry;
       Continuation c = new Continuation(){
          public void receiveResult(Object o){
            FSAddLog logentry = (FSAddLog) o; 
            if(o != null){
              final String name = logentry.getFileName();
              if(!proxy.existsLocally(logentry.getFileName())){
                 ContentHashReference c = logentry.getReference();
                 StorageService ss = post.getStorageService();
                 Continuation store = new Continuation(){
                    public void receiveResult(Object o){
                       proxy.storeData(name, ((FSData) o).getData());
                    }
                    public void receiveException(Exception e) {
                        e.printStackTrace();
                    }
                  };
                  ss.retrieveContentHash(c, store);
               }
               else{
                 proxy.registerFile(name); 
               }
              logentry.getPreviousEntry(this);
            }
         }
         public void receiveException(Exception e) {e.printStackTrace();}
         };
         l.getTopEntry(c);
    } 


    private void initDir(){
      initDirHelper(log, "");
    }

    private void initDirHelper(Log parent, final String s){
      final Object[] logNames = parent.getChildLogNames(); 
      for(int i = 0 ; i < logNames.length; i ++){
           final String name = (String) logNames[i];
           if(!proxy.existsLocally(s + File.separator + name)){ 
              proxy.createLocalDir(s + File.separator + (String) logNames[i]);
           }
           else{
              proxy.registerFile(s + File.separator + name);
           }
           //System.out.println(s + logNames[i]);
           Continuation logC = new Continuation(){
              public void receiveResult(Object o){
                initFiles((Log) o);
                initDirHelper((Log) o, s + File.separator + name);}
              public void receiveException(Exception e) {
                 e.printStackTrace();
              }
           };
           parent.getChildLog(logNames[i], logC);
      }
    }

    /**
     * This method is how the Post layer informs the FSService
     * layer that there is an incoming notification of a new file.
     *
     *@param nm The incoming notification.
     */
    public void notificationReceived(NotificationMessage nm) {
          System.out.println("Message Recieved");	
          if(((FSNotificationMessage)nm).isDirectory()){
               handleDirectoryMessage(nm);
          } 
          else{
               handleFileMessage(nm);
          }
     }


    /**
     * Stores the specified file f, in the single copy store 
     *
     * @param file the file to store
     *
     */
    public void storeFile(final File f){
        System.out.println("Attempting to Store File: " + f);
        Continuation command = new Continuation(){
          public void receiveResult(Object o){
            final Log lg = (Log) o; 
            try{
              byte[] buffer = new byte[(int) f.length()];       
              FileInputStream fis = new FileInputStream(f);
              fis.read(buffer);
              StorageService ss = post.getStorageService();
              FSData data = new FSData(buffer);    
              Continuation store = new Continuation(){
                public void receiveResult(Object o){
                 post.sendGroup(new FSNotificationMessage(FSService.this, pua, new PostGroupAddress("abpost"), (ContentHashReference) o , proxy.getCanonicalName(f)));
                     lg.addLogEntry(new FSAddLog((ContentHashReference) o, proxy.getCanonicalName(f)), new Continuation(){

               public void receiveResult(Object o){}
               public void receiveException(Exception e) {e.printStackTrace();}

});
               }
               
               public void receiveException(Exception e) {
                   e.printStackTrace();
               }
            };
           ss.storeContentHash(data, store);
        }
        catch(FileNotFoundException fnfe){
           fnfe.printStackTrace();
        }
        catch(IOException ioe){
           ioe.printStackTrace();
        }
        }
          public void receiveException(Exception e){
            e.printStackTrace();
          }
        };
        getLogForFile(f, command);
    }

    /**
     * Creates a directory in the system
     *
     * @param f the directory to create
     *
     */
    public void createDir(final File f){
      System.out.println("Calling createDir in FSService");
      Continuation command = new Continuation(){
         public void receiveResult(Object o){
           addLog((Log) o, f.getName());
           post.sendGroup(new FSNotificationMessage(FSService.this, pua, new PostGroupAddress("abpost"), null, proxy.getCanonicalName(f), true));
         }
         public void receiveException(Exception e){
           e.printStackTrace();
         }
      };
      getLogForFile(f, command);
    }

    private void handleDirectoryMessage(NotificationMessage nm){
       System.out.println("Directory Message Recieved");
    }

    private void handleFileMessage(NotificationMessage nm){
       FSNotificationMessage fsnm = (FSNotificationMessage) nm;
       System.out.println(fsnm.getFileName());
       final String name = fsnm.getFileName();
       ContentHashReference c = ((FSNotificationMessage) nm).getContent();
       StorageService ss = post.getStorageService();
       Continuation store = new Continuation(){
          public void receiveResult(Object o){
            proxy.storeData(name, ((FSData) o).getData());
          }
          public void receiveException(Exception e) {
             e.printStackTrace();
          }
        };
        ss.retrieveContentHash(c, store);
     }

   /* returns the log that should be used for this file */
   /* @param f the file to use */
   private void getLogForFile(File f, final Continuation command) {
      final String[] parts = proxy.getCanonicalName(f).split(File.separator);
        System.out.println("Parts length = " + parts.length);
        Continuation logC = new StandardContinuation(command){
          private int i = 0;
          public void receiveResult(Object o){
              if(i == (parts.length - 2))
                 command.receiveResult(o);
              else{
                System.out.println("Calling getLogHelper with " + parts[i + 1]);
                i = i + 1;
                getLogHelper((Log) o, parts[i], this);
              }
          }
        };
        if(parts.length == 1)
            command.receiveResult(log);
        else
            getLogHelper(log, parts[0], logC);
   } 

   private void getLogHelper(Log parent, String child, Continuation command){
        System.out.println("Child = " + child);
        if(parent == null)
           System.out.println("Parent is NULL");
        if(child == null)
           System.out.println("Child is NULL");
        if(command == null)
           System.out.println("Command is NULL");
        parent.getChildLog(child, command); 
   }

   /**
    * Method adds a sublog to a parent log with given name
    *
    * @param parent the parent log to add to
    * @param name the name of the child log
    */
   private void addLog(Log parent, String name){
         System.out.println("Creating new log with name " + name);
         Log child = new Log( name, Id.makeRandomId(new Random()), getPost());
         Continuation addLogC = new Continuation(){
          public void receiveResult(Object o){}
          public void receiveException(Exception e) {
             e.printStackTrace();
          }
        };
         parent.addChildLog(child, addLogC);
     }
}


