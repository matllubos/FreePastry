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

package rice.pastry.routing;

import java.io.*;

/**
 * This is the options for a client to send messages.
 * 
 * @author Tsuen Wan Ngan
 * @author Andrew Ladd
 */

public class SendOptions implements Serializable
{
    private boolean random;
    private boolean noShortCuts;
    private boolean shortestPath;
    private boolean allowMultipleHops;
    
    public static final boolean defaultRandom = false;
    public static final boolean defaultNoShortCuts = true;
    public static final boolean defaultShortestPath = true;
    public static final boolean defaultAllowMultipleHops = true;
    
    /**
     * Constructor.
     */

    public SendOptions() 
    {
	random = defaultRandom;
	noShortCuts = defaultNoShortCuts;
	shortestPath = defaultShortestPath;
	allowMultipleHops = defaultAllowMultipleHops;
    }

    /**
     * Constructor.
     *
     * @param random true if randomize the route
     * @param noShortCuts true if require each routing step to go to a node whose id matches in exactly one more digit
     * @param shortestPath true if require to go to the strictly nearest known node with appropriate node id
     * @param allowMultipleHops true if we allow multiple hops for this transmission, false otherwise.
     */
    
    public SendOptions(boolean random, boolean noShortCuts, boolean shortestPath, boolean allowMultipleHops)
    {
	this.random = random;
	this.noShortCuts = noShortCuts;
	this.shortestPath = shortestPath;
	this.allowMultipleHops = allowMultipleHops;
    }
    
    /**
     * Returns whether randomizations on the route are allowed.
     *
     * @return true if randomizations are allowed.
     */
    
    public boolean canRandom()
    {
	return random;
    }
    
    /**
     * Returns whether it is required for each routing step to go to a 
     * node whose id matches in exactly one more digit.
     *
     * @return true if it is required to go to a node whose id matches in exactly one more digit. 
     */
    
    public boolean makeNoShortCuts()
    {
	return noShortCuts;
    }
    
    /**
     * Returns whether it is required to go to the strictly nearest known node with appropriate node id.
     *
     * @return true if it is required to go to the strictly nearest known node with appropriate node id.
     */
    
    public boolean requireShortestPath()
    {
	return shortestPath;
    }

    /**
     * Returns whether multiple hops are allowed during the transmission of this message.
     *
     * @return true if so, false otherwise.
     */

    public boolean multipleHopsAllowed() 
    {
	return allowMultipleHops;
    }

    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException 
    {
	random = in.readBoolean();
	noShortCuts = in.readBoolean();
	shortestPath = in.readBoolean();
	allowMultipleHops = in.readBoolean();
    }
    
    private void writeObject(ObjectOutputStream out)
	throws IOException, ClassNotFoundException 
    {
	out.writeBoolean(random);
	out.writeBoolean(noShortCuts);
	out.writeBoolean(shortestPath);
	out.writeBoolean(allowMultipleHops);
    }
}








