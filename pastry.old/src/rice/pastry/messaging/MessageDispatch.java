//////////////////////////////////////////////////////////////////////////////
// Rice Open Source Pastry Implementation                  //               //
//                                                         //  R I C E      //
// Copyright (c)                                           //               //
// Romer Gil                   rgil@cs.rice.edu            //   UNIVERSITY  //
// Andrew Ladd                 aladd@cs.rice.edu           //               //
// Tsuen Wan Ngan              twngan@cs.rice.edu          ///////////////////
//                                                                          //
// This program is free software; you can redistribute it and/or            //
// modify it under the terms of the GNU General Public License              //
// as published by the Free Software Foundation; either version 2           //
// of the License, or (at your option) any later version.                   //
//                                                                          //
// This program is distributed in the hope that it will be useful,          //
// but WITHOUT ANY WARRANTY; without even the implied warranty of           //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the            //
// GNU General Public License for more details.                             //
//                                                                          //
// You should have received a copy of the GNU General Public License        //
// along with this program; if not, write to the Free Software              //
// Foundation, Inc., 59 Temple Place - Suite 330,                           //
// Boston, MA  02111-1307, USA.                                             //
//                                                                          //
// This license has been added in concordance with the developer rights     //
// for non-commercial and research distribution granted by Rice University  //
// software and patent policy 333-99.  This notice may not be removed.      //
//////////////////////////////////////////////////////////////////////////////

package rice.pastry.messaging;

import java.util.*;

/**
 * An object which remembers the mapping from names to MessageReceivers
 * and dispatches messages by request.
 *
 * @author Andrew Ladd
 */

public class MessageDispatch 
{
    private HashMap mailbox;

    /**
     * Registers a receiver with the mail service.
     *
     * @param name a name for a receiver.
     * @param receiver the receiver.
     */

    public void registerReceiver(Address address, MessageReceiver receiver) 
    {
	mailbox.put(address, receiver);	
    }

    /**
     * Dispatches a message to the appropriate receiver.
     *
     * @param msg the message.
     * 
     * @return true if message could be dispatched, false otherwise.
     */

    public boolean dispatchMessage(Message msg) 
    {
	MessageReceiver mr = (MessageReceiver) mailbox.get(msg.getDestination());

	if (mr != null) { mr.receiveMessage(msg); return true; }
	else return false;
    }

    /**
     * Constructor.
     */
    
    public MessageDispatch() 
    {
	mailbox = new HashMap();
    }

}
