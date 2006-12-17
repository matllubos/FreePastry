/*
 * Created on Dec 16, 2006
 */
package rice.pastry.socket;

/**
 * Replacement for TimerWeakHashMap for Socket.  This is to handle a bug
 * in the garbage collector that is really hard to reproduce.
 * 
 * @author Jeff Hoye
 */
public interface WeakHashSet {
  /**
   * Throws exception if there is already one available.
   * @param snh
   */
//  public void put(SocketNodeHandle snh);
  public SocketNodeHandle coalesce(SocketNodeHandle snh);
  public SocketNodeHandle get(EpochInetSocketAddress eisa);
}
