package rice.p2p.glacier.v2;

import java.security.*;
import java.io.*;

import rice.environment.logging.Logger;
import rice.p2p.glacier.Fragment;

public class Manifest implements Serializable {
  protected transient byte[] objectHash;
  protected transient byte[][] fragmentHash;
  protected transient byte[] signature;
  protected long expirationDate;
  
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
  
  public boolean validatesFragment(Fragment fragment, int fragmentID, Logger logger) {
    if ((fragmentID < 0) || (fragmentID >= fragmentHash.length))
      return false;
      
    MessageDigest md = null;
    try {
      md = MessageDigest.getInstance("SHA");
    } catch (NoSuchAlgorithmException e) {
      logger.log(Logger.SEVERE, "*** SHA-1 not supported ***"+toStringFull());
      return false;
    }

    md.reset();
    md.update(fragment.getPayload());
    
    byte[] thisHash = md.digest();
    
    if (thisHash.length != fragmentHash[fragmentID].length) {
      logger.log(Logger.WARNING, "*** LENGTH MISMATCH: "+thisHash.length+" != "+fragmentHash[fragmentID].length+" ***"+toStringFull());
      return false;
    }
      
    for (int i=0; i<thisHash.length; i++) {
      if (thisHash[i] != fragmentHash[fragmentID][i]) {
        String s= "*** HASH MISMATCH: POS#"+i+", "+thisHash[i]+" != "+fragmentHash[fragmentID][i]+" ***\n";
        s+="Hash: ";
        for (int j=0; j<thisHash.length; j++)
          s+=thisHash[j];
        s+="\n"+toStringFull();
        logger.log(Logger.WARNING, s);
        return false;
      }
    }
        
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

  private void writeObject(ObjectOutputStream oos) throws IOException {
    oos.defaultWriteObject();
    oos.writeInt(objectHash.length);
    oos.writeInt(fragmentHash.length);
    oos.writeInt(fragmentHash[0].length);
    oos.writeInt(signature.length);
    oos.write(objectHash);
    int dim1 = fragmentHash.length;
    int dim2 = fragmentHash[0].length;
    byte[] fragmentHashField = new byte[dim1*dim2];
    for (int i=0; i<dim1; i++)
      for (int j=0; j<dim2; j++)
        fragmentHashField[i*dim2 + j] = fragmentHash[i][j];
    oos.write(fragmentHashField);
    oos.write(signature);
  }
  
  private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    ois.defaultReadObject();
    int objectHashLength = ois.readInt();
    int fragmentHashLength = ois.readInt();
    int fragmentHashSubLength = ois.readInt();
    int signatureLength = ois.readInt();
    objectHash = new byte[objectHashLength];
    ois.readFully(objectHash, 0, objectHashLength);
    byte[] fragmentHashField = new byte[fragmentHashLength * fragmentHashSubLength];
    ois.readFully(fragmentHashField, 0, fragmentHashLength * fragmentHashSubLength);
    fragmentHash = new byte[fragmentHashLength][fragmentHashSubLength];
    for (int i=0; i<fragmentHashLength; i++)
      for (int j=0; j<fragmentHashSubLength; j++)
        fragmentHash[i][j] = fragmentHashField[i*fragmentHashSubLength + j];
    signature = new byte[signatureLength];
    ois.readFully(signature, 0, signatureLength);
  }
}
