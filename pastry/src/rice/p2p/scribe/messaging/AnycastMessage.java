
package rice.p2p.scribe.messaging;

import java.util.*;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;

/**
 * @(#) AnycastMessage.java The anycast message.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class AnycastMessage extends ScribeMessage {

  /**
   * the content of this message
   */
  protected ScribeContent content;

  /**
   * the list of nodes which we have visited
   */
  protected Vector visited;

  /**
   * the list of nodes which we are going to visit
   */
  protected LinkedList toVisit;

  /**
   * Constructor which takes a unique integer Id
   *
   * @param source The source address
   * @param topic The topic
   * @param content The content
   */
  public AnycastMessage(NodeHandle source, Topic topic, ScribeContent content) {
    super(source, topic);

    this.content = content;
    this.visited = new Vector();
    this.toVisit = new LinkedList();

    addVisited(source);
  }

  /**
   * Returns the content
   *
   * @return The content
   */
  public ScribeContent getContent() {
    return content;
  }

  /**
   * Sets the content
   *
   * @param content The content
   */
  public void setContent(ScribeContent content) {
    this.content = content;
  }

  /**
   * Returns the next handle to visit
   *
   * @return The next handle to visit
   */
  public NodeHandle peekNext() {
    if (toVisit.size() == 0) {
      return null;
    }

    return (NodeHandle) toVisit.getFirst();
  }

  /**
   * Returns the next handle to visit and removes the
   * node from the list.
   *
   * @return The next handle to visit
   */
  public NodeHandle getNext() {
    if (toVisit.size() == 0) {
      return null;
    }
    
    return (NodeHandle) toVisit.removeFirst();
  }

  /**
   * Adds a node to the visited list
   *
   * @param handle The node to add
   */
  public void addVisited(NodeHandle handle) {
    if (handle == null) {
      return;
    }

    if (!visited.contains(handle)) {
      visited.add(handle);
    }

    while (toVisit.remove(handle)) {
      toVisit.remove(handle);
    }
  }

  /**
   * Adds a node the the front of the to-visit list
   *
   * @param handle The handle to add
   */
  public void addFirst(NodeHandle handle) {
    if (handle == null) {
      return;
    }

    if ((!toVisit.contains(handle)) && (!visited.contains(handle))) {
      toVisit.addFirst(handle);
    }
  }

  /**
   * Adds a node the the end of the to-visit list
   *
   * @param handle The handle to add
   */
  public void addLast(NodeHandle handle) {
    if (handle == null) {
      return;
    }

    if ((!toVisit.contains(handle)) && (!visited.contains(handle))) {
      toVisit.addLast(handle);
    }
  }

  /**
   * Removes the node handle from the to visit and visited lists
   *
   * @param handle The handle to remove
   */
  public void remove(NodeHandle handle) {
    if (handle == null) {
      return;
    }

    toVisit.remove(handle);
    visited.remove(handle);
  }
}

