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
public class HelloMessageDigest extends MessageDigest {

  int number;
  
  /**
   * @param local
   * @param remote
   * @param messagLine
   * @param type
   * @param udp
   */
  public HelloMessageDigest(
    int lineNum,
    String local,
    String remote,
    String messageLine,
    int type,
    boolean udp,
    int globalMsgNum) {
    super(lineNum, local, remote, messageLine, type, udp);
    searchForMe = true;
    fixMessageLine();
    parseMessageLine();
  }

  private void fixMessageLine() {
    //[ [PEM PublishMessage[TOPIC <0xEACAF8..>]:0            2105027332] ]
    //[ [PEM PublishMessage[TOPIC <0xFACAF8..>]:9         2105036629] ]
    //{[ [PEM PublishMessage[TOPIC <0xEACAF8..>]:7                2105034628] ]}
    //[ {Hello #3100 from <0x687040..> to <0xFD4D94..> received by null} ]
    //{[ {Hello #3100 from <0x687040..> to <0xFD4D94..> received by null} ]
        if (messageLine.startsWith("{")) {
      messageLine = messageLine.substring(1,messageLine.length()-1);
    }
  }
  
  private void parseMessageLine() {
    //System.out.println(messageLine);    
    //[ [PEM PublishMessage[TOPIC <0xEACAF8..>]:0            2105027332] ]
    try {
      for (int i = 1; i < 10; i++) {    
        String numStr = messageLine.substring(10,10+i);
        //System.out.println("num:"+numStr);
        number = Integer.parseInt(numStr);
      }      
    } catch (Exception e) {
    }
  }

  public int hashCode() {
    int j = local.hashCode()^remote.hashCode()^number;
    return j;
  }

  public boolean equals(Object o) {
    try {
      HelloMessageDigest that = (HelloMessageDigest)o;
      if ((that.number == this.number) &&
          (that.local.equals(this.local)) &&
          (that.remote.equals(this.remote))) {
        return true;
      }
      return false;
    } catch (Exception e) {
      return false;
    }
  }

  public String toString() {
    String s = super.toString();
    return s+","+hashCode();
  }

}
