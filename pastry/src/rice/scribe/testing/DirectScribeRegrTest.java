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

package rice.scribe.testing;

import rice.scribe.testing.*;
import rice.pastry.*;

/**
 * @(#) DirectScribeRegrTest.java
 *
 * A comprehensive regression test suite for Scribe.
 *
 * @version $Id$
 *
 * @author Atul Singh
 * @author Animesh Nandi 
 */

public class DirectScribeRegrTest
{

    public static void main(String args[]) {
	boolean ok1 = true;
	boolean ok2 = true;
	boolean ok = true;

	/**
	 * The comprehensive regression test suite comprises of mainly
	 * two tests -
	 * 1) BasicScribeRegrTest : which tests if the create, subscribe,
	 *    publish and unsubscribe operations of Scribe work.
	 * 2) DirectScribeMaintenanceTest : which tests if the tree 
	 *    maintenance activities of Scribe are successful in repairing
	 *    the topic trees in a scenario of concurrent node failures
	 *    and node joins. 
	 */

	// Setting the seed helps to reproduce the results of the run and 
	// thus aids debugging incase the regression test fails.
	PastrySeed.setSeed((int)System.currentTimeMillis());
	System.out.println("******************************");
	ok1 = BasicScribeRegrTest.start();
	System.out.println("******************************\n");
	System.gc();

	System.out.println("******************************");
	ok2 = DirectScribeMaintenanceTest.start();
	System.out.println("******************************\n");
	
	ok = ok1 && ok2;

	System.out.println("Pastry seed used for Regression test = " + PastrySeed.getSeed());
	if(ok)
	    System.out.println("COMPREHENSIVE SCRIBE REGRESSION TEST - PASSED");
	else
	    System.out.println("COMPREHENSIVE SCRIBE REGRESSION TEST - FAILED");
	    
	System.gc();

    }

}
	
	
