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

package rice.pastry;

import java.util.*;

/**
 * A fairly rudimentary Log class for the moment.
 *
 * @version $Id$
 *
 * @author Sitaram Iyer
 */

public class Log {
    /**
     * Sets the default verbosity level to 5
     */
    private static int verbosity = 5;

    /**
     * Called by Pastry applications to parse for verbosity options.
     *
     * @param args Command-line arguments.
     */
    public static void init(String args[]) {
	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-verbosity") && i+1 < args.length)
		verbosity = Integer.parseInt(args[i+1]);

	    else if (args[i].equals("-verbose"))
		verbosity = 10;

	    else if (args[i].equals("-silent"))
		verbosity = -1;
	}
    }

    /**
     * Called before every println, to check if verbosity levels are okay.
     *
     * @param v required verbosity
     * @return true if verbosity is leq v.
     */
    public static boolean ifp(int v) {
	return (v <= verbosity) ? true : false;
    }
}
