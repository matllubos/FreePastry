package rice.pastry;

import java.util.*;

public class PastrySeed 
{
    public static int getSeed() {
	//return 42;				// for reproducible results
	return (int)System.currentTimeMillis();	// for default behaviour
    }
}
