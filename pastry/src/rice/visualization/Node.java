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
/*
 * Created on Jul 22, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.visualization;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.HashSet;
import java.util.Vector;

import rice.pastry.dist.DistNodeHandle;

/**
 * @author Jeff Hoye
 */
public class Node implements Comparable {
  DistNodeHandle handle;
  Point location;
  Ring ring;
  Rectangle selectionArea;
  Rectangle textLocation;
  
  
  /**
   * Vector of Node 
   * these are neighbors in the same ring.
   */
  Vector neighbors = new Vector();

  /**
   * Vector of Node 
   * These correspond to the same node, but in different rings.
   */
  HashSet associations = new HashSet();

    
  public Node(DistNodeHandle h, Ring r) {
    handle = h;
    ring = r;
  }
    
  public String toString() {
    return ring.name+":"+handle;
  }
  
  public void addAssociation(Node n) {
    if (n.handle.getId().equals(this.handle.getId())) {
      associations.add(n);
    }
  }
  
  public int compareTo(Object o) {
    return handle.getNodeId().compareTo(((Node) o).handle.getNodeId());
  }
}
