/*
 * Created on Aug 16, 2005
 */
package rice.environment.processing.simple;

import java.util.*;

import rice.environment.processing.WorkRequest;

/**
 * @author Jeff Hoye
 */
public class WorkQueue {
  List q = new LinkedList();
  /* A negative capacity, is equivalent to infinted capacity */
  int capacity = -1;

  volatile boolean running = true;
  
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
    while (q.isEmpty() && running) {
      try {
        wait();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    
    if (!running) return null;
    return (WorkRequest) q.remove(0);
  }
  
  public void destroy() {
    running = false;
    synchronized(this) {
      notifyAll(); 
    }
  }

}
