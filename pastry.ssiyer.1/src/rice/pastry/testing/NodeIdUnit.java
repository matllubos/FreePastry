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

package rice.pastry.testing;

import rice.pastry.*;
import java.util.*;

/**
 * NodeIdUnit tests the NodeId class.
 *
 * @author Andrew Ladd
 */

public class NodeIdUnit {
    private NodeId nid;
    private Random rng;
    
    public NodeId createNodeId() 
    {
	byte raw[] = new byte[NodeId.nodeIdBitLength >> 3];
	
	rng.nextBytes(raw);

	NodeId nodeId = new NodeId(raw);

	System.out.println("created node " + nodeId);

	byte copy[] = new byte[raw.length];

	nodeId.blit(copy);

	for (int i=0; i<raw.length; i++) if (copy[i] != raw[i]) System.out.println("blit test failed!");
	
	copy = nodeId.copy();

	for (int i=0; i<raw.length; i++) if (copy[i] != raw[i]) System.out.println("copy test failed!");

	return nodeId;
    }

    public void equalityTest() 
    {
	System.out.println("--------------------------");
	System.out.println("Creating oth");
	NodeId oth = createNodeId();

	if (nid.equals(oth) == false) System.out.println("not equal - as expected.");
	else System.out.println("ALERT: equal - warning this happens with very low probability");

	if (nid.equals(nid) == true) System.out.println("equality seems reflexive.");
	else System.out.println("ALERT: equality is not reflexive.");

	System.out.println("hash code of nid: " + nid.hashCode());
	System.out.println("hash code of oth: " + oth.hashCode());
	System.out.println("--------------------------");
    }

    public void distanceTest() 
    {
	System.out.println("--------------------------");

	System.out.println("creating a and b respectively");
	
	NodeId a = createNodeId();
	NodeId b = createNodeId();
	
	for (int i=0; i<20; i++) {

	    if (a.clockwise(b))
		System.out.println("b is clockwise from a");
	    else
		System.out.println("b is counter-clockwise from a");

	    a = createNodeId();
	    b = createNodeId();
	}

	NodeId.Distance adist = nid.distance(a);
	NodeId.Distance adist2 = a.distance(nid);
	NodeId.Distance bdist = nid.distance(b);
	System.out.println("adist =" + adist + "\n bdist=" + bdist);

	if (adist.equals(adist2) == true) System.out.println("distance seems reflexive");
	else System.out.println("ALERT: distance is non-reflexive.");

	if (adist.equals(bdist) == true) System.out.println("ALERT: nodes seem at equal distance - very unlikely");
	else System.out.println("nodes have different distance as expected.");

	System.out.println("result of comparison with a and b " + adist.compareTo(bdist));
	System.out.println("result of comparison with a to itself " + adist.compareTo(adist2));

	byte[] raw0 = {0,0,0,0,0,0,0,0,0,0,
		       0,0,0,0,0,0};
	byte[] raw80 = {0,0,0,0,0,0,0,0,0,0,
			0,0,0,0,0,-128};
	byte[] raw7f = {-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
			-1,-1,-1,-1,-1,127};

	a = new NodeId(raw80);
	b = new NodeId(raw7f);
	NodeId c = new NodeId(raw0);
	System.out.println("a=" + a + "b=" + b + "c=" + c);
	//a.distance(b);

	if (a.clockwise(c))
	    System.out.println("c is clockwise from a");
	else
	    System.out.println("c is counter-clockwise from a");

	if (b.clockwise(c))
	    System.out.println("c is clockwise from b");
	else
	    System.out.println("c is counter-clockwise from b");
	
	System.out.println("a.distance(b)" + a.distance(b) + "b.distance(a)=" + b.distance(a));
	System.out.println("a.longDistance(b)" + a.longDistance(b) + "b.longDistance(a)=" + b.longDistance(a));
			  



	System.out.println("--------------------------");
    }

    public void baseFiddlingTest() 
    {
	System.out.println("--------------------------");

	String bitRep = "";
	for (int i=0; i<NodeId.nodeIdBitLength; i++) 
	    {
		if (nid.checkBit(i) == true) bitRep = bitRep + "1";
		else bitRep = bitRep + "0";
	    }

	System.out.println(bitRep);

	String digRep = "";
	for (int i=0; i<NodeId.nodeIdBitLength; i++) 
	    {
		digRep = digRep + nid.getDigit(i, 1);
	    }

	System.out.println(digRep);
	
	if (bitRep.equals(digRep) == true) System.out.println("strings the same - as expected");
	else System.out.println("ALERT: strings differ - this is wrong.");

	System.out.println("--------------------------");
    }

    public void msdTest() 
    {
	System.out.println("--------------------------");
	
	System.out.println("creating a and b respectively");
	
	NodeId a = createNodeId();
	NodeId b = createNodeId();

	NodeId.Distance adist = nid.distance(a);
	NodeId.Distance bdist = nid.distance(b);

	System.out.println("result of comparison with a and b " + adist.compareTo(bdist));

	System.out.println("msdb a and nid " + nid.indexOfMSDB(a));
	System.out.println("msdb b and nid " + nid.indexOfMSDB(b));

	if (nid.indexOfMSDB(a) == a.indexOfMSDB(nid)) System.out.println("msdb is symmetric");
	else System.out.println("ALERT: msdb is not symmetric");
	
	System.out.println("msdd a and nid (base 16) " + nid.indexOfMSDD(a, 4));
	System.out.println("msdd b and nid (base 16) " + nid.indexOfMSDD(b, 4));

	System.out.println("--------------------------");
    }

    public NodeIdUnit() 
    {
	rng = new Random();

	System.out.println("Creating nid");
	nid = createNodeId();

	equalityTest();	
	distanceTest();
	baseFiddlingTest();
	msdTest();
    }
    
    public static void main(String args[]) 
    {  
	NodeIdUnit niu = new NodeIdUnit();
    }
}
