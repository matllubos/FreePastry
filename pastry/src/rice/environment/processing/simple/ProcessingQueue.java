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
  volatile boolean running = true;
  
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

