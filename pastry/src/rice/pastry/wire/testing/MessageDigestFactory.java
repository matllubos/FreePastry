package rice.pastry.wire.testing;

/**
 * The purpose of the factory is to generate special message digests
 * for specifically analyzed messages.  
 * 
 * @author Jeff Hoye
 */
public class MessageDigestFactory {
  private static int globalMessageNum = 0;

  /**
   * @param sender
   * @param receiver
   * @param messageLine
   */
  public MessageDigest getMessageDigest(int lineNum, String local, String remote, String messageLine, int type, boolean udp) {
    //<0xB7E51E..>:SEN:[ [PEM PublishMessage[TOPIC <0xEACAF8..>]:0            2105027332] ]
    //ENQ:[ [PEM PublishMessage[TOPIC <0xFACAF8..>]:9         2105036629] ]
    //SEN:{[ [PEM PublishMessage[TOPIC <0xEACAF8..>]:7                2105034628] ]}
    //ENQ:[ {Hello #3100 from <0x687040..> to <0xFD4D94..> received by null} ]
    
    //[ [PEM PublishMessage[TOPIC <0xEACAF8..>]:0            2105027332] ]
    //[ [PEM PublishMessage[TOPIC <0xFACAF8..>]:9         2105036629] ]
    //{[ [PEM PublishMessage[TOPIC <0xEACAF8..>]:7                2105034628] ]}

    if (type == WireFileProcessor.TYPE_REC) {
      String temp = local;
      local = remote;
      remote = temp;
    }

    try {
      String subMsg = messageLine.substring(7,7+15);
      if (subMsg.equals("PublishMessage[") || subMsg.equals(" PublishMessage")) {
        return new PublishMessageDigest(lineNum, local,remote,messageLine,type,udp,globalMessageNum++);
      }
      subMsg = messageLine.substring(3,3+5);
      if (subMsg.equals("Hello") || subMsg.equals("{Hello")) {
        return new HelloMessageDigest(lineNum, local, remote, messageLine, type, udp, globalMessageNum++);
      }
    } catch (StringIndexOutOfBoundsException se) {
      
    }
    
    
    // TODO Auto-generated method stub
    return new MessageDigest(lineNum, local,remote,messageLine,type,udp);
  }

}
