
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

	NodeId nodeId = NodeId.buildNodeId(raw);

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
	
	for (int i=0; i<100; i++) {
	    NodeId.Distance adist = nid.distance(a);
	    NodeId.Distance adist2 = a.distance(nid);
	    NodeId.Distance bdist = nid.distance(b);
	    System.out.println("adist =" + adist + "\n bdist=" + bdist);

	    if (adist.equals(adist2) == true) System.out.println("distance seems reflexive");
	    else System.out.println("ALERT: distance is non-reflexive.");

	    if (adist.equals(bdist) == true) 
		System.out.println("ALERT: nodes seem at equal distance - very unlikely");
	    else System.out.println("nodes have different distance as expected.");

	    System.out.println("result of comparison with a and b " + adist.compareTo(bdist));
	    System.out.println("result of comparison with a to itself " + adist.compareTo(adist2));

	    if (a.clockwise(b))
		System.out.println("b is clockwise from a");
	    else
		System.out.println("b is counter-clockwise from a");

	    NodeId.Distance abs = a.distance(b);
	    NodeId.Distance abl = a.longDistance(b);
	    if (abs.compareTo(abl) != -1)
		System.out.println("ERROR: abs.compareTo(abl)=" + abs.compareTo(abl));

	    System.out.println("abs=" + abs);	    
	    abs.shift(-1, 1);
	    System.out.println("abs.shift(-1)=" + abs);	    
	    abs.shift(1, 0);
	    System.out.println("abs.shift(1)=" + abs);	    
	    if (!abs.equals(a.distance(b))) 
		System.out.println("SHIFT ERROR!");

	    a = createNodeId();
	    b = createNodeId();
	}


	byte[] raw0 = {0,0,0,0,0,0,0,0,0,0,
		       0,0,0,0,0,0};
	byte[] raw80 = {0,0,0,0,0,0,0,0,0,0,
			0,0,0,0,0,-128};
	byte[] raw7f = {-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
			-1,-1,-1,-1,-1,127};
	
	byte[] t1 = {0x62,(byte)0xac,0x0a,0x6d,0x26,0x3a,(byte)0xeb,(byte)0xb1,(byte)0xe4,(byte)0x8e,
			0x25,(byte)0xf2,(byte)0xe5,0x0e,(byte)0xa2,0x13};
	byte[] t2 = {0x3a,0x3f,(byte)0xfa,(byte)0x82,0x00,(byte)0x91,(byte)0xfb,(byte)0x82,(byte)0x9d,(byte)0xd2,
			(byte)0xd8,0x42,(byte)0x86,0x40,0x5d,(byte)0xd7};

	a = NodeId.buildNodeId(t1/*raw80*/);
	b = NodeId.buildNodeId(t2/*raw7f*/);
	NodeId n0 = NodeId.buildNodeId(raw0);
	NodeId n7f = NodeId.buildNodeId(raw7f);
	NodeId n80 = NodeId.buildNodeId(raw80);
	NodeId c = n0;

	System.out.println("a=" + a + "b=" + b + "c=" + c);
	System.out.println("a.clockwise(b)=" + a.clockwise(b));
	System.out.println("a.clockwise(a)=" + a.clockwise(a));
	System.out.println("b.clockwise(b)=" + b.clockwise(b));

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
	System.out.println("a.distance(a)" + a.distance(a) + "a.longDistance(a)=" + a.longDistance(a));

	System.out.println("a.isBetween(a,n7f)=" + a.isBetween(a,n7f));
	System.out.println("a.isBetween(n0,a)=" + a.isBetween(n0,a));

	System.out.println("a.isBetween(n0,n7f)=" + a.isBetween(n0,n7f));
	System.out.println("b.isBetween(n0,n80)=" + b.isBetween(n0,n80));
	System.out.println("a.isBetween(a,n80)=" + a.isBetween(a,n80));
	System.out.println("b.isBetween(n0,b)=" + b.isBetween(n0,b));



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
	NodeId.Distance aldist = nid.longDistance(a);
	NodeId.Distance bldist = nid.longDistance(b);
	
	System.out.println("nid.dist(a)=" + adist);
	System.out.println("nid.longDist(a)=" + aldist);
	System.out.println("nid.dist(b)=" + bdist);
	System.out.println("nid.longDist(b)=" + bldist);

	System.out.println("adist.compareTo(bdist) " + adist.compareTo(bdist));
	System.out.println("aldist.compareTo(bldist) " + aldist.compareTo(bldist));

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

    public void domainPrefixTest() 
    {
	System.out.println("--------------------------");
	
	System.out.println("nid=" + nid);

	for (int b=2; b<7; b++) 
	    for (int row=nid.nodeIdBitLength/b-1; row >=0; row--)
		for (int col=0; col<(1 << b); col++) {
		    Id domainFirst = nid.getDomainPrefix(row,col,0,b);
		    Id domainLast = nid.getDomainPrefix(row,col,-1,b);
		    System.out.println("prefixes " + nid + domainFirst + domainLast);
		    int cmp = domainFirst.compareTo(domainLast);
		    boolean equal = domainFirst.equals(domainLast);
		    if ((cmp == 0) != equal)
			System.out.println("ERROR, compareTo=" + cmp + " equal=" + equal); 
		    if (cmp == 1)
			System.out.println("ERROR, compareTo=" + cmp); 
		}

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
	domainPrefixTest();
    }
    
    public static void main(String args[]) 
    {  
	NodeIdUnit niu = new NodeIdUnit();
    }
}
