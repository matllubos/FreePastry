package rice.p2p.glacier.v2;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Random;

public class BloomFilter implements Serializable {
  private byte bitfield[];
  private int hashParams[];
  
  public BloomFilter(int length, int[] hashParams) {
    bitfield = new byte[(length+7)/8];
    Arrays.fill(bitfield, (byte)0);
    this.hashParams = hashParams;
  }  
  
  public BloomFilter(int length, int numHashes) {
    bitfield = new byte[(length+7)/8];
    Arrays.fill(bitfield, (byte)0);

    int numPrimeCandidates = numHashes*100;
    if (numPrimeCandidates >= (length - 5)) 
      numPrimeCandidates = length - 5;
    int maxFactor = (int)(Math.sqrt(length));
    int offset = length - numPrimeCandidates + 1;
    boolean[] isPrimeH = new boolean[numPrimeCandidates];
    boolean[] isPrimeL = new boolean[maxFactor + 1];
    Arrays.fill(isPrimeH, true);
    Arrays.fill(isPrimeL, true);
    
    for (int i=2; i<=maxFactor; i++) {
      if (isPrimeL[i]) {
        for (int j=0; j<=(int)(maxFactor/i); j++)
          isPrimeL[j*i] = false;
        for (int j=(int)((offset+i-1)/i); j<=(int)(length/i); j++)
          isPrimeH[j*i - offset] = false;
      }
    }
    
    hashParams = new int[numHashes];
    Random rand = new Random();
    for (int i=0; i<numHashes; i++) {
      int index = rand.nextInt(numPrimeCandidates);
      while (!isPrimeH[index])
        index = (index+1) % numPrimeCandidates;
      isPrimeH[index] = false;
      hashParams[i] = offset + index;
    }
  }
  
  private int[] getHashes(byte[] data) {
    long cache = 0;
    int ctr = 0;
    int[] hash = new int[hashParams.length];
    Arrays.fill(hash, 0);
    
    for (int i=0; i<data.length; i++) {
      cache = (cache<<8) + data[i] + ((data[i]<0) ? 256 : 0);
      if (((++ctr) == 7) || (i==(data.length-1))) {
        for (int j=0; j<hashParams.length; j++)
          hash[j] += cache % hashParams[j];
        ctr = 0;
        cache = 0;
      }
    }
    
    for (int j=0; j<hashParams.length; j++)
      hash[j] = hash[j] % hashParams[j];
      
    return hash;
  }
  
  private void dump() {
    for (int i=0; i<bitfield.length*8; i++)
      if ((bitfield[i/8] & (1<<(i&7))) == 0)
        System.out.print("0");
      else
        System.out.print("1");
    System.out.println();
  }
  
  public void add(byte[] data) {
    int[] hash = getHashes(data);
    for (int i=0; i<hashParams.length; i++)
      bitfield[hash[i]/8] |= (1<<(hash[i]&7));
  }
  
  public boolean contains(byte[] data) {
    int[] hash = getHashes(data);
    
    for (int i=0; i<hashParams.length; i++)
      if ((bitfield[hash[i]/8] & (1<<(hash[i]&7))) == 0)
        return false;
        
    return true;
  }
  
  public static void main(String arg[]) {
    BloomFilter b = new BloomFilter(20, 4);
    
    byte[] a = new byte[1];
    a[0] = (byte)231;
    b.add(a);
    byte[] c = new byte[1];
    c[0] = (byte)117;
    b.add(c);
    System.out.println("Contains 231: "+b.contains(a));
    System.out.println("Contains 117: "+b.contains(c));
    byte[] d = new byte[1];
    d[0] = (byte)71;
    System.out.println("Contains 71: "+b.contains(d));
  }
}    
    
