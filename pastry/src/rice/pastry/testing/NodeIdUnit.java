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

package rice.pastry.testing;

import rice.pastry.*;
import java.util.*;

/**
 * NodeIdUnit tests the NodeId class.
 *
 * @version $Id$
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

	for (int i=2; i<=6; i++) { 
	    int msdd;

	    System.out.println("msdd a and nid (base " + (1 << i) + ") " + (msdd = nid.indexOfMSDD(a, i)) + 
			       " val=" + a.getDigit(msdd,i) + "," + nid.getDigit(msdd,i));
	    System.out.println("msdd b and nid (base " + (1 << i) + ") " + (msdd = nid.indexOfMSDD(b, i)) +
			       " val=" + b.getDigit(msdd,i) + "," + nid.getDigit(msdd,i));
	}

	System.out.println("--------------------------");
    }


    public void alternateTest() 
    {
	System.out.println("--------------------------");
	
	System.out.println("nid=" + nid);

	for (int b=2; b<7; b++) 
	    for (int num=2; num<=(1 << b); num*=2)
		for (int i=1; i<num; i++) 
		    System.out.println("alternate (b=" + b + ") " + i + ":" + 
				       nid.getAlternateId(num, b, i));


	System.out.println("--------------------------");
    }

    public NodeIdUnit() 
    {
	rng = new Random(PastrySeed.getSeed());

	System.out.println("Creating nid");
	nid = createNodeId();

	equalityTest();	
	distanceTest();
	baseFiddlingTest();
	msdTest();
	alternateTest();
    }
    
    public static void main(String args[]) 
    {  
	NodeIdUnit niu = new NodeIdUnit();
    }
}
