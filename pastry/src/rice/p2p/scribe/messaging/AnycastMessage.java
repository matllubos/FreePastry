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

  // the content of this message
  /**
   * DESCRIBE THE FIELD
   */
  protected ScribeContent content;

  // the list of nodes which we have visited
  /**
   * DESCRIBE THE FIELD
   */
  protected Vector visited;

  // the list of nodes which we are going to visit
  /**
   * DESCRIBE THE FIELD
   */
  protected LinkedList toVisit;

  /**
   * Constructor which takes a unique integer Id
   *
   * @param source The source address
   * @param topic DESCRIBE THE PARAMETER
   * @param content DESCRIBE THE PARAMETER
   */
  public AnycastMessage(NodeHandle source, Topic topic, ScribeContent content) {
    super(source, topic);

    this.content = content;
    this.visited = new Vector();
    this.toVisit = new LinkedList();
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
   * Returns the next handle to visit
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
}

