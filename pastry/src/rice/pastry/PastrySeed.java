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

package rice.pastry;

import java.util.*;

/**
 * @(#) PastrySeed.java
 * 
 * The static methods in this class help to SET and GET the value of the seed
 * that will be used by all instances of random number generators. 
 * Setting the value of the seed at the start of the simulation helps in 
 * producing deterministic results which will help in debugging applications
 * written on Pastry.
 * By default, the GET method returns an arbitrary integer as the seed unless
 * the SET method has been previously invoked. 
 *
 * @version $Id$ 
 * 
 * @author Animesh Nandi
 * @author Atul Singh
 */


public class PastrySeed 
{

    private static boolean deterministic = false; // default value;
    private static int seedValue = 0; // default value;
    
    public static int getSeed() {
	
	int seed;

	if(deterministic)
	    return seedValue;
	else
	    return (int)System.currentTimeMillis();
    }

    public static void setSeed(int value) {
	// Sets the deterministic flag to true, so that subsequent getSeed()
	// will return this value;
	deterministic = true; 
	seedValue = value;
	return;
    }

}








