/*
 * Created on Aug 9, 2005
 */
package rice.environment.processing.simple;

import java.util.*;

/**
 * @author Jeff Hoye
 */
public class ProcessingQueue {
  
  List q = new LinkedList();
  int capacity = -1;
  boolean running = true;
  
  public ProcessingQueue() {
     /* do nothing */
  }
  
  public ProcessingQueue(int capacity) {
     this.capacity = capacity;
  }
  
  public synchronized int getLength() {
    return q.size();
  }
  
  public synchronized void enqueue(ProcessingRequest request) {
    if (capacity < 0 || q.size() < capacity) {
      q.add(request);
      notifyAll();
    } else {
      request.returnError(new ProcessingQueueOverflowException());
    }
  }
  
  public synchronized ProcessingRequest dequeue() {
    while (q.isEmpty() && running) {
      try {
        wait();
      } catch (InterruptedException e) {
      }
    }
    if (!running) return null;
    return (ProcessingRequest) q.remove(0);
  }

  public void destroy() {
    running = false;
    synchronized(this) {
      notifyAll(); 
    }
  }
  
  public class ProcessingQueueOverflowException extends Exception {
  }
}

