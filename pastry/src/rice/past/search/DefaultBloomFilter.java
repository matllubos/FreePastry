package rice.past.search;

import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * TODO: Derek Ruths finish this comment
 * 
 * @author Derek Ruths
 */
public class DefaultBloomFilter implements BloomFilter {

    private boolean[] filter = null;
    private int size;
    private int numBytes;
    
    /**
     * 
     */
    public DefaultBloomFilter(int size) {
    
        // round the size up to the nearest complete-byte size
        int bitSize = roundSizeUp(size);
        this.size = (int) Math.pow(2, bitSize);
        this.numBytes = (int) (bitSize / 8);        
        
        System.out.println("BLOOM FILTER ACTUAL SIZE = " + this.size); 
        System.out.println("BLOOM FILTER BYTE SIZE = " + this.numBytes);   

        // initialize the filter
        this.filter = new boolean[this.size];            
    }
    
    /**
     * Round the integer given up to the nearest power of 2.
     * It must also be a byte size.
     */
    private int roundSizeUp(int size) {
        
        int powerOf2 = (int) Math.ceil(Math.log(size) / Math.log(2));
        
        powerOf2 += 8 - (powerOf2 % 8);
        
        return powerOf2; 
    }
    
    /**
     * @see rice.past.search.BloomFilter#contains(Object)
     */
    public boolean contains(Object obj) {
        
        int index = this.getBloomIndex(obj.toString());
        
        return this.filter[index];
    }

    /**
     * @see rice.past.search.BloomFilter#add(Object)
     */
    public void add(Object obj) {
        
        int index = this.getBloomIndex(obj.toString());
        
        this.filter[index] = true;
    }

    private int getBloomIndex(String string) {
     
        MessageDigest md = null;

        try {
            md = MessageDigest.getInstance("SHA");
        } catch (NoSuchAlgorithmException e) {
            System.err.println("No SHA support!");
        }

        md.update(string.getBytes());

        byte[] array = md.digest(); //new byte[4]; // assume at most 4 bytes
        
        for(int i = this.numBytes; i < array.length; i++) {
            array[i] = 0;    
        }
        
        /*
        try {
            md.digest(array, 0, this.numBytes);
        } catch(DigestException de) {
            de.printStackTrace();
            return 0;
        }
        */
        
        return bytesToInt(array);
    }
    
    private int bytesToInt(byte[] array) {
       
        // assume we'll only have at most 4 bytes (1 int)
        int result = (((int)array[3] & 0xFF) << 24);
        result += (((int)array[2] & 0xFF) << 16);
        result += (((int)array[1] & 0xFF) << 8);
        result += ((int)array[0] & 0xFF);

        return result;
    }
    
    public static void main(String[] args) {
        
        DefaultBloomFilter bf = new DefaultBloomFilter(10);
                    
        bf.add("Hello");
        bf.add("Hi there!");
        
        System.out.println("CONTAINS 'BYE': " + bf.contains("BYE!"));
        System.out.println("CONTAINS 'Hello': " + bf.contains("Hello"));
        System.out.println("CONTAINS 'Hi there!': " + bf.contains("Hi there!"));
        System.out.println("CONTAINS 'Hello2': " + bf.contains("Hello2"));
        
        
        return;
    }
}

