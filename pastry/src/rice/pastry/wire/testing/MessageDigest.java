package rice.pastry.wire.testing;

/**
 * This is the basic message that the WireFileProcessor creates.
 * A message is parsed into a digest and can be analyzed later.
 * 
 * @author Jeff Hoye
 */
public class MessageDigest {
  
  public int type = -1;
  public String messageLine;
  public String local;
  public String remote;
  public int hashcode;
  public boolean udp = true;
  public int lineNumber;
  
  /**
   * Have MessageDigestFactory set this to true if you want to analyze
   * these messages in the future.
   */
  public boolean searchForMe = false;
  
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
