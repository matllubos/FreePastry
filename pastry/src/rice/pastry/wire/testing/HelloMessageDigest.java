/**
 * "FreePastry" Peer-to-Peer Application Development Substrate Copyright 2002,
 * Rice University. All rights reserved. Redistribution and use in source and
 * binary forms, with or without modification, are permitted provided that the
 * following conditions are met: - Redistributions of source code must retain
 * the above copyright notice, this list of conditions and the following
 * disclaimer. - Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution. -
 * Neither the name of Rice University (RICE) nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission. This software is provided by RICE and the
 * contributors on an "as is" basis, without any representations or warranties
 * of any kind, express or implied including, but not limited to,
 * representations or warranties of non-infringement, merchantability or fitness
 * for a particular purpose. In no event shall RICE or contributors be liable
 * for any direct, indirect, incidental, special, exemplary, or consequential
 * damages (including, but not limited to, procurement of substitute goods or
 * services; loss of use, data, or profits; or business interruption) however
 * caused and on any theory of liability, whether in contract, strict liability,
 * or tort (including negligence or otherwise) arising in any way out of the use
 * of this software, even if advised of the possibility of such damage.
 */
package rice.pastry.wire.testing;

/**
 * Helper class for interpreting HelloMessages.
 * 
 * @author Jeff Hoye
 */
public class HelloMessageDigest extends MessageDigest {

  /**
   * Hello Message Number
   */
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
