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

package rice.pastry.client;

import rice.pastry.messaging.*;
import rice.pastry.*;

/**
 * This is an interface for a Pastry application object.
 * 
 * @author Tsuen Wan Ngan
 * @author Andrew Ladd
 */

public interface PastryClient extends MessageReceiver
{
    /**
     * Get default address.  This address will be registered to this
     * client.
     *
     * @return an address for this client.
     */
    
    public Address getDefaultAddress();

    /**
     * Get default credentials.  
     *
     * @return credentials for this client.
     */
    
    public Credentials getDefaultCredentials();
    
    
    /**
     * Called by Pastry when this node's leaf set is changed.
     *
     * @param change true if the node joined the leaf set; false if it left
     * @param handle the node handle of the node which joined or left.
     */
    
    public void leafSetChanged(boolean change, NodeHandle handle);
    
    /**
     * Called by Pastry when this node's neighborhood set is changed.
     *
     * @param change true if a node joined the leaf set; false if it left.
     * @param handle the node handle of the node which joined or left.
     */
    
    public void neighborhoodSetChanged(boolean change, NodeHandle handle);
    
    /**
     * Called by Pastry when this node's routing table is 
     * changed.
     *
     * @param row the row number of the changed entry
     * @param col the column number of the changed entry
     * @param change true if a node joined the leaf set; false if it left.
     * @param handle the node handle of the node which joined or left.
     */
    
    public void routingTableChanged(int row, int col, boolean change, NodeHandle handle);
}
