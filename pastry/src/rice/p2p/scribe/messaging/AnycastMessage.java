
package rice.p2p.scribe.messaging;

import java.io.IOException;
import java.util.*;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.rawserialization.*;

/**
 * @(#) AnycastMessage.java The anycast message.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class AnycastMessage extends ScribeMessage {

  public static final short TYPE = 1;

  /**
   * the content of this message
   */
  protected RawScribeContent content;

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
    this(source, topic, content instanceof RawScribeContent ? (RawScribeContent)content : new JavaSerializedScribeContent(content));
  }
  
  public AnycastMessage(NodeHandle source, Topic topic, RawScribeContent content) {
    super(source, topic);
//    if (content == null) throw new IllegalArgumentException
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
    if (content == null) return null; 
    if (content.getType() == 0) return ((JavaSerializedScribeContent)content).getContent();
    return content;
  }

  /**
   * Sets the content
   *
   * @param content The content
   */
  public void setContent(RawScribeContent content) {
    this.content = content;
  }

  public void setContent(ScribeContent content) {
    if (content instanceof RawScribeContent) {
      setContent(content); 
    } else {
      setContent(new JavaSerializedScribeContent(content));
    }
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
  
  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE; 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
//  super.serialize(buf); // note, can't make this a hierarchy, because of the super() must be first line rule in java
    buf.writeByte((byte)0); // version
    serializeHelper(buf);
    
    if (content == null) {
      buf.writeBoolean(false);
    } else {
      buf.writeBoolean(true);
      buf.writeShort(content.getType());
      content.serialize(buf);
    }    
  }
  
  /**
   * Use this to allow SubscribeMessage to extend this, but not have the version number
   * nor the content.
   */
  protected void serializeHelper(OutputBuffer buf) throws IOException {    
    super.serialize(buf);
    
    buf.writeInt(toVisit.size());
    Iterator i = toVisit.iterator();
    while(i.hasNext()) {
      ((NodeHandle)i.next()).serialize(buf); 
    }
    
    buf.writeInt(visited.size());
    i = visited.iterator();
    while(i.hasNext()) {
      ((NodeHandle)i.next()).serialize(buf); 
    }
  }
  
  public static AnycastMessage build(InputBuffer buf, Endpoint endpoint, ScribeContentDeserializer scd) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        return new AnycastMessage(buf, endpoint, scd, true);
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }
  
  /**
   * Protected because it should only be called from an extending class, to get version
   * numbers correct.
   * 
   * @param readContent should probably be false if this class is extended, true if not.  The parameter is wether or not
   * this message could have a ScribeContent, the SubscribeMesage never has a content, so it should be false
   */
  protected AnycastMessage(InputBuffer buf, Endpoint endpoint, ScribeContentDeserializer cd, boolean readContent) throws IOException {
    super(buf, endpoint);
    
    toVisit = new LinkedList();
    int toVisitLength = buf.readInt();
    for (int i = 0; i < toVisitLength; i++) {
      toVisit.addLast(endpoint.readNodeHandle(buf)); 
    }
    
    int visitedLength = buf.readInt();
    visited = new Vector(visitedLength);
    for (int i = 0; i < visitedLength; i++) {
      visited.add(endpoint.readNodeHandle(buf)); 
    }
    
    if (readContent) {
      // this can be done lazilly to be more efficient, must cache remaining bits, endpoint, cd, and implement own InputBuffer
      if (buf.readBoolean()) {
        short contentType = buf.readShort();
        if (contentType == 0) {
          content = new JavaSerializedScribeContent(cd.deserializeScribeContent(buf, endpoint, contentType));
        } else {
          content = (RawScribeContent)cd.deserializeScribeContent(buf, endpoint, contentType); 
        }
      }  
    }
  }

}

