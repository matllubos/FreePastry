/*
 * Created on Aug 9, 2005
 */
package rice.environment.processing.simple;

/**
 * @author Jeff Hoye
 */
public class ProcessingThread extends Thread {
  ProcessingQueue queue;
   
  volatile boolean running = false;
  
   public ProcessingThread(String name, ProcessingQueue queue){
     super(name);
     this.queue = queue;
   }
   
   public void run() {
     running = true;
     while (running) {
       ProcessingRequest e = queue.dequeue(); 
       if (e != null) e.run();
     }
   }
   
   @SuppressWarnings("deprecation")
   public void destroy() {
     running = false;
   }
} 

