package rice.p2p.saar;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.util.*;
import java.math.*;

/**
 * @author Animesh Nandi
 */
public class SaarAnalysis  {
    
    public static int f = -1; //fanout
    public static int d = -1; // depth
    public static int n = -1; // total nodes
    public double a = 0;


    public SaarAnalysis() {
	if((f==-1) && (d ==-1) && (n==-1)) {
	    for(f=2; f <= 10; f++) {
		for(d=1; d < 20; d++) {
		    n = (int)(f*(Math.pow(f,d) -1)/(f-1));
		    a = percentAffected(f,d);
		    //System.out.println("x = " + 5);
		    System.out.println("F: " + f + " D: " + d + " N: " + n + " A: " + a);
		    
		}
	    }




	}

    }



    public double percentAffected(int f, int d) {
	double val;
	val = 100* (((f-1)*(d-1)*Math.pow(f,d))  - f*(Math.pow(f,d-1) - 1))/(f*Math.pow((Math.pow(f,d) -1),2));
	return val;

    }

    
    public static void main(String[] args) throws Exception {
	// We do the parsing of the options
      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-help")) {
	      System.out.println("Usage: SaarAnalysis -f -d -n");
	      System.exit(1);
	  }
      }

      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-d") && i + 1 < args.length) {
	      int val = Integer.parseInt(args[i + 1]);
	      d = val;
	      break;
	  }
      }

      
      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-f") && i + 1 < args.length) {
	      int val = Integer.parseInt(args[i + 1]);
	      f = val;
	      break;
	  }
      }



      
      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-n") && i + 1 < args.length) {
	      int val = Integer.parseInt(args[i + 1]);
	      n = val;
	      break;
	  }
      }


      new SaarAnalysis();


    } 

}