package rice.p2p.glacier.v2;

import java.security.*;
import java.io.Serializable;
import rice.p2p.glacier.Fragment;

public class StorageManifest implements Serializable {
  protected byte[] objectHash;
  protected byte[][] fragmentHash;
  protected long expirationDate;
  protected byte[] signature;
  
  public StorageManifest(byte[] objectHash, byte[][] fragmentHash, long expirationDate) {
    this.objectHash = objectHash;
    this.fragmentHash = fragmentHash;
    this.expirationDate = expirationDate;
    this.signature = null;
  }
  
  public void dump(byte[] data) {
    String hex = "0123456789ABCDEF";
    for (int i=0; i<data.length; i++) {
      int d = data[i];
      if (d<0)
        d+= 256;
      int hi = (d>>4);
      int lo = (d&15);
        
      System.out.print(hex.charAt(hi)+""+hex.charAt(lo));
      if (((i%16)==15) || (i==(data.length-1)))
        System.out.println();
      else
        System.out.print(" ");
    }
  }

  public byte[] getObjectHash() {
    return objectHash;
  }
  
  public byte[] getFragmentHash(int fragmentID) {
    return fragmentHash[fragmentID];
  }
  
  public byte[] getSignature() {
    return signature;
  }

  public void setSignature(byte[] signature) {
    this.signature = signature;
  }
  
  public long getExpirationDate() {
    return expirationDate;
  }
  
  public void update(long newExpirationDate, byte[] newSignature) {
    expirationDate = newExpirationDate;
    signature = newSignature;
  }
  
  public boolean validatesFragment(Fragment fragment, int fragmentID) {
//System.out.println("--A FID "+fragmentID+" vs "+fragmentHash.length);
    if ((fragmentID < 0) || (fragmentID >= fragmentHash.length))
      return false;
      
//System.out.println("--B find SHA?");
    MessageDigest md = null;
    try {
      md = MessageDigest.getInstance("SHA");
    } catch (NoSuchAlgorithmException e) {
      return false;
    }

    md.reset();
    md.update(fragment.payload);
    
    byte[] thisHash = md.digest();
    
//System.out.println("--C proper length?");
    if (thisHash.length != fragmentHash[fragmentID].length)
      return false;
      
/*System.out.println("--D proper value?");
System.out.print("      INPUT    = ");
dump(thisHash);
System.out.print("      MANIFEST = ");
dump(fragmentHash[fragmentID]); */
    for (int i=0; i<thisHash.length; i++)
      if (thisHash[i] != fragmentHash[fragmentID][i])
        return false;
        
//System.out.println("--E true");
    return true;
  }
}
