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
public class PublishMessageDigest extends MessageDigest {

  String topic;
  int seqNumber;
  double time;
  int globalMsgNum;
  
  /**
   * @param local
   * @param remote
   * @param messagLine
   * @param type
   * @param udp
   */
  public PublishMessageDigest(
    int lineNum,
    String local,
    String remote,
    String messageLine,
    int type,
    boolean udp,
    int globalMsgNum) {
    super(lineNum, local, remote, messageLine, type, udp);
    this.globalMsgNum = globalMsgNum;
    searchForMe = true;
    fixMessageLine();
    parseMessageLine();
  }

  private void fixMessageLine() {
    //[ [PEM PublishMessage[TOPIC <0xEACAF8..>]:0            2105027332] ]
    //[ [PEM PublishMessage[TOPIC <0xFACAF8..>]:9         2105036629] ]
    //{[ [PEM PublishMessage[TOPIC <0xEACAF8..>]:7                2105034628] ]}
    if (messageLine.startsWith("{")) {
      messageLine = messageLine.substring(1,messageLine.length()-1);
    }
  }
  
  private void parseMessageLine() {
    //System.out.println(messageLine);    
    //[ [PEM PublishMessage[TOPIC <0xEACAF8..>]:0            2105027332] ]
    topic = messageLine.substring(36-7,46-7);
    
    int l = messageLine.length();

    String seq = messageLine.substring(49-7,l-15);
    
    String tim = messageLine.substring(l-13,l-3);    

//    System.out.println(topic+","+seq+","+tim);
  try {
    seqNumber = Integer.parseInt(seq);
  } catch (NumberFormatException nfe) {
    seq = messageLine.substring(49-7,l-16);
    seqNumber = Integer.parseInt(seq);
  }
  
    time = Double.parseDouble(tim);
  }

  public int hashCode() {
    int u = udp?0:1;
    int j = local.hashCode()^remote.hashCode()^topic.hashCode()^seqNumber; //^ u;// ^ time;
    return j;
  }

  public boolean equals(Object o) {
    try {
      PublishMessageDigest that = (PublishMessageDigest)o;
      if ((that.local.equals(this.local)) &&
          (that.remote.equals(this.remote)) &&
      //(that.udp == this.udp) &&
          (that.seqNumber == this.seqNumber) &&
          //(that.time == this.time) &&
          (that.topic.equals(this.topic))) {
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
