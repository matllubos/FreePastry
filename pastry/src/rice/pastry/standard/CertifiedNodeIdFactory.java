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

package rice.pastry.standard;

import rice.pastry.*;
import java.io.*;
import rice.serialization.*;

/**
 * Builds nodeIds in a certified manner, guaranteeing that a given node will always
 * have the same nodeId.  NOTE:  Actual certification is not yet implemented, rather, 
 * using this factory simply guarantees that the node's nodeId will never change.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class CertifiedNodeIdFactory implements NodeIdFactory {
  
  public static String NODE_ID_FILENAME = ".nodeId-";
  
  protected int port;
  protected IPNodeIdFactory realFactory;
  
  /**
   * Constructor.
   */
  public CertifiedNodeIdFactory(int port) {
    this.port = port;
    this.realFactory = new IPNodeIdFactory(port);
  }
  
  /**
   * generate a nodeId
   *
   * @return the new nodeId
   */
  public NodeId generateNodeId() {
    try {
      File f = new File(NODE_ID_FILENAME + port);
      
      if (f.exists()) {
        XMLObjectInputStream xois = new XMLObjectInputStream(new FileInputStream(f));
        return (NodeId) xois.readObject();
      } else {
        NodeId result = realFactory.generateNodeId();
        XMLObjectOutputStream xoos = new XMLObjectOutputStream(new FileOutputStream(f));
        xoos.writeObject(result);
        xoos.close();
        
        return result;
      }
    } catch (IOException e) {
      System.out.println(e);
      return null;
    } catch (ClassNotFoundException e) {
      System.out.println(e);
      return null;
    }
  }
  
}

