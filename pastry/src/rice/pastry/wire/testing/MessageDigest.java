/*
 * Created on Feb 3, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package rice.pastry.wire.testing;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class MessageDigest {
  
  public int type = -1;
  public String messageLine;
  public String local;
  public String remote;
  public int hashcode;
  public boolean udp = true;
  public int lineNumber;
  
  public MessageDigest(int lineNumber, String local, String remote, String messagLine,
                       int type, boolean udp) {
    this.lineNumber = lineNumber;
    this.local = local;
    this.remote = remote;
    this.messageLine = messagLine;
    this.type = type;
    this.udp = udp;                       
  }
  
  public int hashCode() {
    return -45;
  }
  
  public boolean equals(Object o) {    
    try {
      MessageDigest that = (MessageDigest)o;
      if (that instanceof PublishMessageDigest) {
        return false;
      }
      return true;
    } catch (ClassCastException cce) {      
    }
    return false;
  }
  
  public String toString() {
    String typeStr = WireFileProcessor.getType(type);
    return "MD:"+lineNumber+":"+local+"<->"+remote+":"+typeStr+","+udp+":"+messageLine;
  }
  
}
