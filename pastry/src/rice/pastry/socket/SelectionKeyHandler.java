/**************************************************************************

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

package rice.pastry.socket;

import java.nio.channels.*;

/**
 * This interface is designed to be a callback mechanism from the SelectorManager.
 * Once the manager has determines that something has happened, it informs the
 * appropriate SelectionKeyHandler via this interface.
 * 
 * The SelectionKeyHandler which is interested in being notified of events 
 * relating to the SelectionKey should attach itself to the SelectionKey via
 * the attach() method.  The SelectorManager will then call that SelectionKeyHandler's
 * methods.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public interface SelectionKeyHandler {
  
  /**
   * Method which is called when the key becomes acceptable.
   * 
   * @param key The key which is acceptable.
   */
  public void accept(SelectionKey key);
  
  /**
   * Method which is called when the key becomes connectable.
   * 
   * @param key The key which is connectable.
   */
  public void connect(SelectionKey key);
  
  /**
   * Method which is called when the key becomes readable.
   * 
   * @param key The key which is readable.
   */
  public void read(SelectionKey key);
  
  /**
   * Method which is called when the key becomes writable.
   * 
   * @param key The key which is writable.
   */
  public void write(SelectionKey key);
  
}
