
package rice.pastry;

import java.util.*;

/**
 * A fairly rudimentary Log class for the moment.
 *
 * Usage: if (Log.ifp(v)) System.out.println("foo");
 * Conventions: Use 5 for most messages, seen by default.
 *              Use 6 or 7 for junky informational messages.
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
