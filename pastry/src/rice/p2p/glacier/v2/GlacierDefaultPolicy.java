package rice.p2p.glacier.v2;

import rice.p2p.glacier.*;
import rice.Continuation;
import java.io.*;
import java.util.Arrays;
import java.security.*;

public class GlacierDefaultPolicy implements GlacierPolicy {

  protected ErasureCodec codec;
  
  public GlacierDefaultPolicy(ErasureCodec codec) {
    this.codec = codec;
  }

  public boolean checkSignature(StorageManifest manifest, VersionKey key) {
    if (manifest.getSignature() == null)
      return false;
      
    return Arrays.equals(manifest.getSignature(), key.toByteArray());
  }

  protected void signManifest(StorageManifest manifest, VersionKey key) {
    manifest.setSignature(key.toByteArray());
  }

  public void prefetchLocalObject(VersionKey key, Continuation command) {
    command.receiveResult(null);
  }

  public Serializable decodeObject(Fragment[] fragments) {
    return (Serializable) codec.decode(fragments);
  }
  
  public StorageManifest[] createManifests(VersionKey key, Serializable obj, Fragment[] fragments, long expiration) {
    byte bytes[] = null;
    try {
      ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
      ObjectOutputStream objectStream = new ObjectOutputStream(byteStream);

      objectStream.writeObject(obj);
      objectStream.flush();

      bytes = byteStream.toByteArray();
    } catch (IOException ioe) {
      System.out.println("Cannot serialize object: "+ioe);
      return null;
    }

    /* Get the SHA-1 hash object. */

    MessageDigest md = null;
    try {
      md = MessageDigest.getInstance("SHA");
    } catch (NoSuchAlgorithmException e) {
      System.out.println("No SHA support!");
      return null;
    }

    /* Compute the hash values. */

    byte[][] fragmentHash = new byte[fragments.length][];
    for (int i = 0; i < fragments.length; i++) {
      md.reset();
      md.update(fragments[i].payload);
      fragmentHash[i] = md.digest();
    }

    byte[] objectHash = null;
    md.reset();
    md.update(bytes);
    objectHash = md.digest();
    
    /* Create the storage manifest */
    
    StorageManifest[] manifests = new StorageManifest[fragments.length];
    for (int i=0; i<fragments.length; i++) {
      manifests[i] = new StorageManifest(objectHash, fragmentHash, expiration);
      signManifest(manifests[i], key);
    }
    
    return manifests;
  }
  
  public Fragment[] encodeObject(Serializable obj) {
    System.out.println("Serialize object: " + obj);

    byte bytes[] = null;
    try {
      ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
      ObjectOutputStream objectStream = new ObjectOutputStream(byteStream);

      objectStream.writeObject(obj);
      objectStream.flush();

      bytes = byteStream.toByteArray();
    } catch (IOException ioe) {
      System.out.println("Cannot serialize object: "+ioe);
      return null;
    }

    System.out.println("Create fragments: " + obj);
    Fragment[] fragments = codec.encode(bytes);
    System.out.println("Completed: " + obj);
    
    return fragments;
  }

}
