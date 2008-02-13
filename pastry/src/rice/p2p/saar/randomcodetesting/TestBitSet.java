package rice.p2p.saar.randomcodetesting;

import java.util.BitSet;



public class TestBitSet {



    public TestBitSet() {


    }


     public static void main(String[] args) throws Exception {
	 
	 BitSet bitset = new BitSet();
	 bitset.set(0);
         System.out.println("size(0):" + bitset.size() + ", len: " + bitset.length());
	 bitset.set(100);
         System.out.println("size(0, 100):" + bitset.size() + ", len: " + bitset.length());
	 bitset.set(50);
         System.out.println("size(0, 100, 50):" + bitset.size() + ", len: " + bitset.length());
	 bitset.set(150);
         System.out.println("size(0, 100, 50, 150):" + bitset.size() + ", len: " + bitset.length());
	 bitset.set(1000);
         System.out.println("size(0, 100, 50, 150, 1000):" + bitset.size() + ", len: " + bitset.length());

         System.out.println("size(0, 100, 50, 150, 1000): get(50)" + bitset.get(50) + ", " + bitset.size() + ", len: " + bitset.length());
	 
         System.out.println("size(0, 100, 50, 150, 1000): get(2000)" + bitset.get(2000) + ", " + bitset.size() + ", len: " + bitset.length());
	 
	 bitset.clear(4000);
	 System.out.println("size(0, 100, 50, 150, 1000): clear(4000)" + bitset.get(50) + ", " + bitset.size() + ", len: " + bitset.length());
     }


}