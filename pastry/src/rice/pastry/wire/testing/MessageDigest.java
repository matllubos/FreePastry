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
