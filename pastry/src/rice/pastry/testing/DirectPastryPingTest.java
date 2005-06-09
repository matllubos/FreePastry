package rice.pastry.testing;

import rice.environment.Environment;
import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.standard.*;
import rice.pastry.join.*;

import java.util.*;
import java.io.*;
import java.lang.*;

/**
 * DirectPastryPingTest
 * 
 * A performance test suite for pastry.
 * 
 * @version $Id$
 * 
 * @author Rongmei Zhang
 */

public class DirectPastryPingTest {

  public DirectPastryPingTest() {
  }

  private static boolean parseInput(String in, Environment environment) {
    StringTokenizer tokened = new StringTokenizer(in);
    if (!tokened.hasMoreTokens()) {
      return false;
    }

    String token = tokened.nextToken();
    int n = -1;
    int k = -1;
    SinglePingTest spt;
    int i;

    if (token.startsWith("q")) { //quit
      return true;
    } else if (token.startsWith("s")) { //standalone
      Vector trlist = new Vector();

      //	    k = 200000;

      for (i = 0; i < 8; i++) {
        n = k = (i + 1) * 1000;
        PingTestRecord tr = new PingTestRecord(n, k, environment.getParameters().getInt("pastry_rtBaseBitLength"));
        spt = new SinglePingTest(tr, environment);
        spt.test();
        System.out.println(tr.getNodeNumber() + "\t" + tr.getAveHops() + "\t"
            + tr.getAveDistance());
        //		System.out.println( "probability of " + i + " hops: " +
        // tr.getProbability()[i] );
      }
      /*
       * for( i=0; i <10; i++ ){ trlist.addElement( new PingTestRecord(
       * (i+1)*10000, k ) ); spt = new SinglePingTest(
       * (PingTestRecord)(trlist.lastElement()) ); spt.test(); PingTestRecord tr =
       * (PingTestRecord)trlist.elementAt(i); System.out.println(
       * tr.getNodeNumber() + "\t" + tr.getAveHops() +"\t"+ tr.getAveDistance() );
       * System.out.println( "probability of " + i + " hops: " +
       * tr.getProbability()[i] ); }
       */
    }
    return false;
  }

  public static void main(String args[]) throws IOException {
    boolean quit = false;
    Environment env = new Environment();
    BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
    String command = null;

    System.out.println("Usage: s - run standalone test");
    System.out.println("       q - quit");

    while (!quit) {
      try {
        command = input.readLine();
      } catch (Exception e) {
        System.out.println(e);
      }
      quit = parseInput(command, env);
    }

  }
}

