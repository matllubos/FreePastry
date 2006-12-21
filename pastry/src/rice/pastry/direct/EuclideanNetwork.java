/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

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
package rice.pastry.direct;
import java.lang.*;

import java.util.*;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.random.RandomSource;
import rice.pastry.*;
import rice.pastry.messaging.*;

/**
 * Euclidean network topology and idealized node life. Emulates a network of nodes that are randomly
 * placed in a plane. Proximity is based on euclidean distance in the plane.
 *
 * @version $Id$
 * @author Andrew Ladd
 * @author Rongmei Zhang
 */
public class EuclideanNetwork extends BasicNetworkSimulator {

  final int side;
  /**
   * Constructor.
   */
  public EuclideanNetwork(Environment env) {
    super(env);
    side = (int)(maxDiameter/Math.sqrt(2.0));
//    System.out.println("side:"+side);    
  }

  public NodeRecord generateNodeRecord() {
    return new EuclideanNodeRecord(); 
  }
  
  /**
   * Initialize a random Euclidean NodeRecord
   *
   * @version $Id$
   * @author amislove
   */
  private class EuclideanNodeRecord implements NodeRecord {
    /**
     * The euclidean position.
     */
    public int x, y;

    public boolean alive;

    /**
     * Constructor for NodeRecord.
     *
     * @param nh 
     */
    public EuclideanNodeRecord() {
      x = random.nextInt() % side;
      y = random.nextInt() % side;

      alive = true;
    }

    public float proximity(NodeRecord that) {
      return Math.round((networkDelay(that)*2.0));
    }
    
    public float networkDelay(NodeRecord that) {
      EuclideanNodeRecord nr = (EuclideanNodeRecord)that;
      int dx = x - nr.x;
      int dy = y - nr.y;
      
      float ret = (float)Math.sqrt(dx * dx + dy * dy);
//      int ret = (int)Math.round(sqrt);
      if ((ret < 2.0) && !this.equals(that)) return (float)2.0;
      
      return ret;
    }
    
    public String toString() {
      return "ENR("+x+","+y+")"; 
    }

    public void markDead() {
    }
    
  }  
}
