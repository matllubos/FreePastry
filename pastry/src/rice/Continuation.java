/*************************************************************************

"Free Pastry" Peer-to-Peer Application Development Substrate 

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

package rice;

/**
 * Asynchronously receives the result to a given method call, using
 * the command pattern.
 * 
 * Implementations of this class contain the remainder of a computation
 * which included an asynchronous method call.  When the result to the
 * call becomes available, the receiveResult method on this command
 * is called.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public interface Continuation {

  /**
   * Called when a previously requested result is now availble.
   *
   * @param result The result of the command.
   */
  public void receiveResult(Object result);

  /**
   * Called when an execption occured as a result of the
   * previous command.
   *
   * @param result The exception which was caused.
   */
  public void receiveException(Exception result);

  /**
   * This class is a Continuation provided for simplicity which
   * passes any errors up to the parent Continuation which it
   * is constructed with.  Subclasses should implement the
   * receiveResult() method with the appropriate behavior.
   */
  public static abstract class StandardContinuation implements Continuation {

    /**
     * The parent continuation
     */
    protected Continuation parent;

    /**
     * Constructor which takes in the parent continuation
     * for this continuation.
     *
     * @param continuation The parent of this continuation
     */
    public StandardContinuation(Continuation continuation) {
      parent = continuation;
    }

    /**
     * Called when an execption occured as a result of the
     * previous command.  Simply calls the parent continuation's
     * receiveResult() method.
     *
     * @param result The exception which was caused.
     */
    public void receiveException(Exception result) {
      parent.receiveException(result);
    }
  }
  
  /**
   * This class is a Continuation provided for simplicity which
   * passes any results up to the parent Continuation which it
   * is constructed with.  Subclasses should implement the
   * receiveException() method with the appropriate behavior.
   */
  public static abstract class ErrorContinuation implements Continuation {
    
    /**
    * The parent continuation
     */
    protected Continuation parent;
    
    /**
     * Constructor which takes in the parent continuation
     * for this continuation.
     *
     * @param continuation The parent of this continuation
     */
    public ErrorContinuation(Continuation continuation) {
      parent = continuation;
    }
    
    /**
     * Called when an the result is availble.  Simply passes the result
     * to the parent;
     *
     * @param result The result
     */
    public void receiveResult(Object result) {
      parent.receiveResult(result);
    }
  }

  /**
   * This class is a Continuation provided for simplicity which
   * listens for any errors and ignores any success values.  This
   * Continuation is provided for testing convience only and should *NOT* be
   * used in production environment.
   */
  public static class ListenerContinuation implements Continuation {

    /**
     * The name of this continuation
     */
    protected String name;
    
    /**
     * Constructor which takes in a name
     *
     * @param name A name which uniquely identifies this contiuation for
     *   debugging purposes
     */
    public ListenerContinuation(String name) {
      this.name = name;
    }
    
    /**
     * Called when a previously requested result is now availble. Does
     * absolutely nothing.
     *
     * @param result The result
     */
    public void receiveResult(Object result) {
    }

    /**
     * Called when an execption occured as a result of the
     * previous command.  Simply prints an error message to the screen.
     *
     * @param result The exception which was caused.
     */
    public void receiveException(Exception result) {
      System.out.println("ERROR - Received exception " + result + " during task " + name);
      result.printStackTrace();
    }
  }
  
  /**
   * This class is a Continuation provided for simplicity which
   * passes both results and exceptions to the receiveResult() method.
   */
  public abstract static class SimpleContinuation implements Continuation {
    
    /**
     * Called when an execption occured as a result of the
     * previous command.  Simply prints an error message to the screen.
     *
     * @param result The exception which was caused.
     */
    public void receiveException(Exception result) {
      receiveResult(result);
    }
  }

  /**
   * This class provides a continuation which is designed to be used from
   * an external thread.  Applications should construct this continuation pass it
   * in to the appropriate method, and then call sleep().  Once the thread is woken
   * up, the user should check exceptionThrown() to determine if an error was
   * caused, and then call getException() or getResult() as apprpritate.
   */
  public static class ExternalContinuation implements Continuation {

    protected Exception exception;
    protected Object result;

    public synchronized void receiveResult(Object o) {
      result = o;
      notify();
    }

    public synchronized void receiveException(Exception e) {
      exception = e;
      notify();
    }

    public Object getResult() {
      if (exception != null) {
        throw new IllegalArgumentException("Exception was thrown in ExternalContinuation, but getResult() called!");
      }
        
      return result;
    }

    public Exception getException() {
      return exception;
    }

    public synchronized void sleep() {
      try {
        if ((result == null) && (exception == null)) {
          wait();
        }
      } catch (InterruptedException e) {
        exception = e;
      }
    }
    
    public boolean exceptionThrown() {
      return (exception != null);
    }
  }
}
