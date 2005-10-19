/*************************************************************************

"Free Pastry" Peer-to-Peer Application Development Substrate

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

package rice.p2p.splitstream;

import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;

/**
 * The interface defines the methods that a splitStream object must implement. The methods are for
 * creating a channel and for attaching to an already existing channel
 *
 * @version $Id$
 * @author Ansley Post
 * @author Alan Mislove
 */
public interface SplitStream {

  /**
   * A SplitStream application calls this method to join a channel.
   *
   * @param id The id of the channel
   * @return A channel object used for subsequent operations on the desired content stream
   */
  public Channel attachChannel(ChannelId id);

  /**
   * A SplitStream application calls this method when it wishes to distribute content, creating a
   * new channel object.
   *
   * @param id The id of the channel
   * @return A new channel object
   */
  public Channel createChannel(ChannelId id);

  /**
   * Returns all of the channels on this local splitstream
   *
   * @return All of the channels currently being received by this splitstream
   */
  public Channel[] getChannels();

}
