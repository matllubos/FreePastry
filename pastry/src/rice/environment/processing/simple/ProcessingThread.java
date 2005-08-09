/*
 * Created on Aug 9, 2005
 */
package rice.environment.processing.simple;

/**
 * @author Jeff Hoye
 */
public class ProcessingThread extends Thread {
  ProcessingQueue queue;
   
  boolean running = false;
  
   public ProcessingThread(ProcessingQueue queue){
     super("Dedicated Processing Thread");
     this.queue = queue;
   }
   
   public void run() {
     running = true;
     while (running) {
       ProcessingRequest e = queue.dequeue(); 
       if (e != null) e.run();
     }
   }
   
   public void destroy() {
     running = false;
   }
} 

