package rice.p2p.glacier.v2;

import rice.environment.Environment;
import rice.environment.logging.*;
import rice.p2p.commonapi.Endpoint;
import rice.p2p.glacier.*;
import rice.p2p.past.PastContent;
import rice.p2p.past.rawserialization.*;
import rice.p2p.util.rawserialization.SimpleOutputBuffer;
import rice.Continuation;
import java.io.*;
import java.util.Arrays;
import java.security.*;

public class GlacierDefaultPolicy implements GlacierPolicy {

  protected ErasureCodec codec;
  protected String instance;
  protected Environment environment;
  protected Logger logger;
  
  public GlacierDefaultPolicy(ErasureCodec codec, String instance, Environment env) {
    this.codec = codec;
    this.instance = instance;
    this.environment = env;
    logger = environment.getLogManager().getLogger(GlacierDefaultPolicy.class, instance);
  }

  public boolean checkSignature(Manifest manifest, VersionKey key) {
    if (manifest.getSignature() == null)
      return false;
      
    return Arrays.equals(manifest.getSignature(), key.toByteArray());
  }

  protected void signManifest(Manifest manifest, VersionKey key) {
    manifest.setSignature(key.toByteArray());
  }

  public void prefetchLocalObject(VersionKey key, Continuation command) {
    command.receiveResult(null);
  }

  public PastContent decodeObject(Fragment[] fragments, Endpoint endpoint, PastContentDeserializer pcd) {
    return codec.decode(fragments, endpoint, pcd);
  }
  
  public Manifest[] createManifests(VersionKey key, RawPastContent obj, Fragment[] fragments, long expiration) {
    try {    
      SimpleOutputBuffer sob = new SimpleOutputBuffer();
      sob.writeShort(obj.getType());
      obj.serialize(sob);
      return createManifestsHelper(key, sob.getBytes(), sob.getWritten(), fragments, expiration);
    } catch (IOException ioe) {
      if (logger.level <= Logger.WARNING) logger.log( 
          "Cannot serialize object: "+ioe);
      return null;
    }
  }
  
  public Manifest[] createManifests(VersionKey key, PastContent obj, Fragment[] fragments, long expiration) {
    return createManifests(key, obj instanceof RawPastContent ? (RawPastContent)obj : new JavaSerializedPastContent(obj), fragments, expiration);
  }
  
  private Manifest[] createManifestsHelper(VersionKey key, byte[] bytes, int length, Fragment[] fragments, long expiration) {

    /* Get the SHA-1 hash object. */

    MessageDigest md = null;
    try {
      md = MessageDigest.getInstance("SHA");
    } catch (NoSuchAlgorithmException e) {
      if (logger.level <= Logger.WARNING) logger.log( 
          "No SHA support!");
      return null;
    }

    /* Compute the hash values. */

    byte[][] fragmentHash = new byte[fragments.length][];
    for (int i = 0; i < fragments.length; i++) {
      md.reset();
      md.update(fragments[i].getPayload());
      fragmentHash[i] = md.digest();
    }

    byte[] objectHash = null;
    md.reset();
    md.update(bytes,0,length);
    objectHash = md.digest();
    
    /* Create the manifest */
    
    Manifest[] manifests = new Manifest[fragments.length];
    for (int i=0; i<fragments.length; i++) {
      manifests[i] = new Manifest(objectHash, fragmentHash, expiration);
      signManifest(manifests[i], key);
    }
    
    return manifests;
  }
  
  public Fragment[] encodeObject(RawPastContent obj, boolean[] generateFragment) {
    if (logger.level <= Logger.FINER) logger.log( 
        "Serialize object: " + obj);

    try {
      SimpleOutputBuffer sob = new SimpleOutputBuffer();
      sob.writeShort(obj.getType());
      obj.serialize(sob);
      return encodeObjectHelper(obj, sob.getBytes(), sob.getWritten(), generateFragment); 
    } catch (IOException ioe) {
      if (logger.level <= Logger.WARNING) logger.log( 
          "Cannot serialize object: "+ioe);
      return null;
    }
  }
  
  public Fragment[] encodeObject(PastContent obj, boolean[] generateFragment) {
    return encodeObject(obj instanceof RawPastContent ? (RawPastContent)obj : new JavaSerializedPastContent(obj), generateFragment);
  }

    
  private Fragment[] encodeObjectHelper(PastContent obj, byte[] bytes, int length, boolean[] generateFragment) {  
    if (logger.level <= Logger.FINER) logger.log( 
        "Create fragments: " + obj);
    Fragment[] fragments = codec.encode(bytes, length, generateFragment);
    if (logger.level <= Logger.FINER) logger.log( 
        "Completed: " + obj);
    
    return fragments;
  }

  public Manifest updateManifest(VersionKey key, Manifest manifest, long newExpiration) {
    if (!checkSignature(manifest, key))
      return null;

    Manifest newManifest = new Manifest(manifest.getObjectHash(), manifest.getFragmentHashes(), newExpiration);
    signManifest(newManifest, key);
    
    return newManifest;
  }
}
