/*
 * Created on May 24, 2005
 *
 */
package rice.p2p.util.testing;

import rice.environment.random.RandomSource;
import rice.environment.random.simple.SimpleRandomSource;
import rice.p2p.util.MathUtils;

/**
 * MathUtils unit tests
 * 
 * @author jstewart
 * @param argv The command line arguments
 */
public class MathUtilsUnit {
  
  public static void main(String[] args) {
    System.out.println("MathUtils Test Suite");
    System.out.println("-------------------------------------------------------------");
    System.out.println("  Running Tests");

    System.out.print("    Testing hexadecimal conversion\t\t\t");

    byte[] testHexBytes = new byte[] {(byte) 0xa7, (byte) 0xb3, (byte) 0x00, (byte) 0x12, (byte) 0x4e};
    String result = MathUtils.toHex(testHexBytes);
    
    if (result.equals("a7b300124e")) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + testHexBytes);
      System.out.println("    Output:\t" + result);
    }
    
    System.out.print("    Testing long conversion\t\t\t\t");
    long testLong = Long.parseLong("0123456789ABCDEF", 16);

    byte[] testLongByte = MathUtils.longToByteArray(testLong);

    if ((testLongByte[0] == (byte) 0x01) &&
      (testLongByte[1] == (byte) 0x23) &&
      (testLongByte[2] == (byte) 0x45) &&
      (testLongByte[3] == (byte) 0x67) &&
      (testLongByte[4] == (byte) 0x89) &&
      (testLongByte[5] == (byte) 0xAB) &&
      (testLongByte[6] == (byte) 0xCD) &&
      (testLongByte[7] == (byte) 0xEF)) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + testLong);
      System.out.println("    Output:\t" + testLongByte[0] + " " + testLongByte[1] + " " +
        testLongByte[2] + " " + testLongByte[3]);
    }

    System.out.print("    Testing int->byte[]->int conversion\t\t\t");
    
    RandomSource r = new SimpleRandomSource();
    boolean passed = true;

  		for (int n=0; n<100000; n++) {
  	    int l = r.nextInt();
  	    byte[] ar = MathUtils.intToByteArray(l);
  	    int res = MathUtils.byteArrayToInt(ar);
  	    /*
  	    long l = r.nextLong();
  	    byte[] ar = longToByteArray(l);
  	    long result = byteArrayToLong(ar);
  	    */

  	    if (res != l) {
  	      passed = false;
  	      System.out.println("[ FAILED ]");
  	      System.out.println("input:  "+l);
  	      System.out.print  ("byte[]: ");
  	      for (int i=0; i<ar.length; i++) {
  	        System.out.print(ar[i]+" ");
  	      }
  	      System.out.println();
  	      System.out.println("output: "+result);
  	      break;
  	    	}
  		}
  		
  		if (passed) System.out.println("[ PASSED ]");

    System.out.print("    Testing long->byte[]->long conversion\t\t\t");
    
    passed = true;

  		for (int n=0; n<100000; n++) {
  	    long l = r.nextLong();
  	    byte[] ar = MathUtils.longToByteArray(l);
  	    long res = MathUtils.byteArrayToLong(ar);

  	    if (res != l) {
  	      passed = false;
  	      System.out.println("[ FAILED ]");
  	      System.out.println("input:  "+l);
  	      System.out.print  ("byte[]: ");
  	      for (int i=0; i<ar.length; i++) {
  	        System.out.print(ar[i]+" ");
  	      }
  	      System.out.println();
  	      System.out.println("output: "+result);
  	      break;
  	    	}
  		}
  		
  		if (passed) System.out.println("[ PASSED ]");
  		
    System.out.println("-------------------------------------------------------------");
  }
}
