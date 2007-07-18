package org.mpisws.p2p.transport;

import java.io.IOException;
import java.util.Map;

/**
 * Notified when there is a problem not related to an outgoing message/socket,
 * or when a callback isn't provided for a message.
 *  
 * @author Jeff Hoye
 *
 * @param <Identifier> the type of identifier at this layer
 * @param <E> the types of exceptions to expect
 */
public interface ErrorHandler<Identifier> {
  /**
   * @param i the sender of the message (as can best be determined)
   * @param bytes the entire message/socket header
   * @param location the location in the bytes that is unexpected
   */
  public void receivedUnexpectedData(Identifier i, byte[] bytes, int location, Map<String, Integer> options);

  /**
   * We got an exception.
   * 
   * @param i the identifier responsible (if any)
   * @param error the exception
   */
  public void receivedException(Identifier i, Throwable error);
}
