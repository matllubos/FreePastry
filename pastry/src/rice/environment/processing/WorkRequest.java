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
 * Created on Aug 16, 2005
 */
package rice.environment.processing;

import rice.Continuation;
import rice.selector.SelectorManager;

/**
 * Extend this class and implement doWork() if you need to do blocking disk IO.
 * 
 * This is primarily used by Persistence.
 * 
 * @author Jeff Hoye
 */
public abstract class WorkRequest implements Runnable {
  private Continuation c;
  private SelectorManager selectorManager;
  
  public WorkRequest(Continuation c, SelectorManager sm){
    this.c = c;
    this.selectorManager = sm;
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
     // long start = environment.getTimeSource().currentTimeMillis();
      final Object result = doWork();
     // System.outt.println("PT: " + (environment.getTimeSource().currentTimeMillis() - start) + " " + toString());
      selectorManager.invoke(new Runnable() {
        public void run() {
          returnResult(result);
        }
        
        public String toString() {
          return "invc result of " + c;
        }
      });
    } catch (final Exception e) {
      selectorManager.invoke(new Runnable() {
        public void run() {
          returnError(e);
        }
        
        public String toString() {
          return "invc error of " + c;
        }
      });
    }
  }
  
  public abstract Object doWork() throws Exception;
}
