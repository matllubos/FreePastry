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

package rice.pastry.routing;

import java.io.*;

/**
 * This is the options for a client to send messages.
 * 
 * @version $Id$
 *
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








