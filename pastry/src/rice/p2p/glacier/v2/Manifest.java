package rice.p2p.glacier.v2;

import java.security.*;
import java.io.Serializable;
import rice.p2p.glacier.Fragment;

public class Manifest implements Serializable {
  protected byte[] objectHash;
  protected byte[][] fragmentHash;
  protected long expirationDate;
  protected byte[] signature;
  
  public Manifest(byte[] objectHash, byte[][] fragmentHash, long expirationDate) {
    this.objectHash = objectHash;
    this.fragmentHash = fragmentHash;
    this.expirationDate = expirationDate;
    this.signature = null;
  }

  public byte[] getObjectHash() {
    return objectHash;
  }
  
  public byte[] getFragmentHash(int fragmentID) {
    return fragmentHash[fragmentID];
  }

  public byte[][] getFragmentHashes() {
    return fragmentHash;
  }
  
  public byte[] getSignature() {
    return signature;
  }

  public void setSignature(byte[] signature) {
    this.signature = signature;
  }
  
  public long getExpiration() {
    return expirationDate;
  }
  
  public void update(long newExpirationDate, byte[] newSignature) {
    expirationDate = newExpirationDate;
    signature = newSignature;
  }
  
  public boolean validatesFragment(Fragment fragment, int fragmentID) {
    if ((fragmentID < 0) || (fragmentID >= fragmentHash.length))
      return false;
      
    MessageDigest md = null;
    try {
      md = MessageDigest.getInstance("SHA");
    } catch (NoSuchAlgorithmException e) {
      return false;
    }

    md.reset();
    md.update(fragment.payload);
    
    byte[] thisHash = md.digest();
    
    if (thisHash.length != fragmentHash[fragmentID].length)
      return false;
      
    for (int i=0; i<thisHash.length; i++)
      if (thisHash[i] != fragmentHash[fragmentID][i])
        return false;
        
    return true;
  }

  private static String dump(byte[] data, boolean linebreak) {
    final String hex = "0123456789ABCDEF";
    String result = "";
    
    for (int i=0; i<data.length; i++) {
      int d = data[i];
      if (d<0)
        d+= 256;
      int hi = (d>>4);
      int lo = (d&15);
        
      result = result + hex.charAt(hi) + hex.charAt(lo);
      if (linebreak && (((i%16)==15) || (i==(data.length-1))))
        result = result + "\n";
      else if (i!=(data.length-1))
        result = result + " ";
    }
    
    return result;
  }

  public String toString() {
    return "[Manifest obj=["+dump(objectHash, false)+"] expires="+expirationDate+"]";
  }
  
  public String toStringFull() {
    String result = "";
    
    result = result + "Manifest (expires "+expirationDate+")\n";
    result = result + "  - objectHash = ["+dump(objectHash, false)+"]\n";
    result = result + "  - signature  = ["+dump(signature, false)+"]\n";
    for (int i=0; i<fragmentHash.length; i++) 
      result = result + "  - fragmHash"+i+" = ["+dump(fragmentHash[i], false)+"]\n";
      
    return result;
  }
}
