package rice.post.storage;

import java.security.*;
import java.security.spec.*;
import java.util.*;
import java.math.*;
import java.io.*;
import javax.crypto.*;
import javax.crypto.spec.*;

import rice.*;
import rice.Continuation.*;
import rice.p2p.aggregation.*;
import rice.p2p.commonapi.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.p2p.glacier.VersioningPast;
import rice.p2p.glacier.v2.GlacierContentHandle;

import rice.post.*;
import rice.post.log.*;
import rice.post.security.*;

/**
 * This class represents a service which stores data in PAST.  This
 * class supports two types of data: content-hash blocks and private-key
 * signed blocks.  This class will automatically format and store data,
 * as well as retrieve and verify the stored data.
 * 
 * @version $Id$
 */
public class StorageService {
  
  /**
   * The default timeout period of objects (3 weeks)
   */
  protected long timeoutInterval;

  /**
   * The address of the user running this storage service.
   */
  private PostEntityAddress entity;
  
  /**
   * The PAST service used for distributed persistant storage.
   */
  private Past immutablePast;
  
  /**
   * The PAST service used for distributed persistant storage.
   */
  private Past mutablePast;
  
  /**
   * The keyPair used to sign and verify objects
   */
  private KeyPair keyPair;

  /**
   * Stored data waiting for verification
   */
  private Hashtable pendingVerification;
  
  /**
   * The factory for creating ids
   */
  private IdFactory factory;
  
  /**
   * The random number generator
   */
  private Random rng;

  /**
   * Lifetime of log head backups
   */
  private long BACKUP_LIFETIME = 1000 * 60 * 60 * 24 * 7;
  
  /**
   * Contructs a StorageService given a PAST to run on top of.
   *
   * @param past The PAST service to use.
   * @param credentials Credentials to use to store data.
   * @param keyPair The keypair to sign/verify data with
   */
  public StorageService(PostEntityAddress address, Past immutablePast, Past mutablePast, IdFactory factory, KeyPair keyPair, long timeoutInterval) {
    this.entity = address;
    this.immutablePast = immutablePast;
    this.mutablePast = mutablePast;
    this.keyPair = keyPair;
    this.factory = factory;
    this.timeoutInterval = timeoutInterval;
    
    rng = new Random();
    pendingVerification = new Hashtable();
  }

  public Id getRandomNodeId() {
    byte[] data = new byte[20];
    rng.nextBytes(data);
    
    return factory.buildId(data);
  }
  
  /**
   * Internal method which returns what the timeout should be for an
   * object inserted now.  Basically, does System.currentTimeMillis() +
   * timeoutInterval.
   *
   * @return The default timeout period for an object inserted now
   */
  protected long getTimeout() {
    return System.currentTimeMillis() + timeoutInterval;
  }

  /**
   * Stores a PostData in the PAST storage system, in encrypted state,
   * and returns a pointer and key to the data object.
   *
   * This first encrypts the PostData using it's hash value as the
   * key, and then stores the ciphertext at the value of the hash of
   * the ciphertext.
   *
   * Once the data has been stored, the command.receiveResult() method
   * will be called with a ContentHashReference as the argument.
   *
   * @param data The data to store.
   * @param command The command to run once the store has completed.
   */
  public void storeContentHash(final PostData data, Continuation command) {
    try {
      byte[] plainText = SecurityUtils.serialize(data);
      byte[] hash = SecurityUtils.hash(plainText);
      byte[] cipherText = SecurityUtils.encryptSymmetric(plainText, hash);
      byte[] loc = SecurityUtils.hash(cipherText);
      
      final Id location = factory.buildId(loc);
      
      final byte[] key = hash;
      
      final ContentHashData chd = new ContentHashData(location, cipherText);
      
      immutablePast.lookupHandles(location, immutablePast.getReplicationFactor()+1, new StandardContinuation(command) {
        public void receiveResult(Object o) {
          PastContentHandle[] handles = (PastContentHandle[]) o;
          
          for (int i=0; i<handles.length; i++) 
            // the object already exists - simply refresh and return a reference
            if (handles[i] != null) {
              final ContentHashReference ref = data.buildContentHashReference(location, key);
              
              refreshContentHash(new ContentHashReference[] {ref}, new StandardContinuation(parent) {
                public void receiveResult(Object o) {
                  parent.receiveResult(ref);
                }
              });
              
              return;
            }
          
          Continuation result = new StandardContinuation(parent) {
            public void receiveResult(Object o) {
              Boolean[] results = (Boolean[]) o;
              int failed = 0;
              
              for (int i=0; i<results.length; i++) {
                if ((results[i] == null) || (! results[i].booleanValue())) 
                  failed++;
              }
              
              if (failed <= results.length/2) 
                parent.receiveResult(data.buildContentHashReference(location, key));
              else 
                parent.receiveException(new IOException("Storage of content hash data into PAST failed - had " + failed + "/" + results.length + " failures."));
            }
          };
          
          // Store the content hash data in PAST
          if (immutablePast instanceof GCPast) 
            ((GCPast) immutablePast).insert(chd, getTimeout(), result);
          else 
            immutablePast.insert(chd, result);
        }
      });
    } catch (IOException e) {
      command.receiveException(e);
    }
  }

  /**
   * This method retrieves a given PostDataReference object from the
   * network. This method also performs the verification checks and
   * decryption necessary.
   *
   * Once the data has been stored, the command.receiveResult() method
   * will be called with a PostData as the argument.
   *
   * @param reference The reference to the PostDataObject
   * @param command The command to run once the store has completed.
   */
  public void retrieveContentHash(final ContentHashReference reference, Continuation command) {
    immutablePast.lookup(reference.getLocation(), new StandardContinuation(command) {
      public void receiveResult(Object o) {
        try {
          ContentHashData chd = (ContentHashData) o;
          
          if (chd == null)
            throw new StorageException("Content hash data not found in PAST!");
          
          byte[] key = reference.getKey();        
          byte[] cipherText = chd.getData();
          byte[] plainText = SecurityUtils.decryptSymmetric(cipherText, key);
          Object data = SecurityUtils.deserialize(plainText);
          
          // Verify hash(cipher) == location
          if (! Arrays.equals(SecurityUtils.hash(cipherText), reference.getLocation().toByteArray())) 
            throw new StorageException("Hash of cipher text does not match location.");
          
          // Verify hash(plain) == key
          if (! Arrays.equals(SecurityUtils.hash(plainText), key)) 
            throw new StorageException("Hash of retrieved content does not match key.");
          
          parent.receiveResult((PostData) data);
        } catch (ClassCastException cce) {
          parent.receiveException(new StorageException("ClassCastException while retrieving data: " + cce));
        } catch (IOException ioe) {
          parent.receiveException(new StorageException("IOException while retrieving data: " + ioe));
        } catch (ClassNotFoundException cnfe) {
          parent.receiveException(new StorageException("ClassNotFoundException while retrieving data: " + cnfe));
        } catch (PostException pe) {
          parent.receiveException(pe);
        }
      }
    });
  }
  
  /**
   * This method "refreshes" a list of ContentHashReferences, which ensures that
   * all of the referenced objects are not collected by the underlying store.
   * This method should be invoked upon objects which the user is interested in, 
   * but the user did not create (i.e. The parts of an email the user has recevied).
   *
   * @param references The references to refresh
   * @param command The command to run once done
   */
  public void refreshContentHash(ContentHashReference[] references, Continuation command) {
    if (immutablePast instanceof GCPast) {
      Id[] ids = new Id[references.length];
    
      for (int i=0; i<ids.length; i++)
        ids[i] = references[i].getLocation();
      
      System.out.println("CALLING REFERSH WITH " + ids.length + " OBJECTS!");
      ((GCPast) immutablePast).refresh(ids, getTimeout(), new StandardContinuation(command) {
        public void receiveResult(Object o) {
          parent.receiveResult(Boolean.TRUE);
        }
      });
    } else {
      command.receiveResult(Boolean.TRUE);
    }
  }
  
  /**
   * This method backs up all of the provided logs by inserting them into the immutable
   * store with appropriate version numbers.
   *
   * @param references The references to refresh
   * @param command The command to run once done
   */
  public void backupLogs(final PostLog log, final Log[] logs, Continuation command) {
    long time = ((long) System.currentTimeMillis() / PostImpl.BACKUP_INTERVAL) * PostImpl.BACKUP_INTERVAL;
    storeSigned(new GroupData(logs), log.getLocation(), time, System.currentTimeMillis() + BACKUP_LIFETIME, keyPair, immutablePast, command);
  }
  
  /**
   * This method performs an emergency recovery of the logs by reinserting them into the
   * provided PAST store.
   *
   */
  public static void recoverLogs(final Id location, final long timestamp, final KeyPair keyPair, final Past immutablePast, final Past mutablePast, Continuation command) {
    final long version = (timestamp / PostImpl.BACKUP_INTERVAL) * PostImpl.BACKUP_INTERVAL;
    System.out.println("COUNT: "+System.currentTimeMillis()+" Timestamp is "+timestamp+", using version "+version);
    ((VersioningPast)immutablePast).lookupHandles(location, version, immutablePast.getReplicationFactor()+1, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        PastContentHandle[] handles = (PastContentHandle[]) o;
        GlacierContentHandle handle = null;

        for (int i=0; i<handles.length; i++) {
          GlacierContentHandle thisH = (GlacierContentHandle) handles[i];
          
          if ((thisH != null) && ((handle == null) || (thisH.getVersion() > handle.getVersion())))
            handle = thisH;
        }
        
        if (handle == null) {
          parent.receiveException(new StorageException("All handles of log backup were null!"));
          return;
        }
        
        immutablePast.fetch(handle, new StandardContinuation(parent) {
          public void receiveResult(Object o) {
            try {
              SignedData data = (SignedData) o;
              
              if (data == null) 
                throw new StorageException("Log backup not found!");

              System.out.println("COUNT: "+System.currentTimeMillis()+" Log backup found!");

              GroupData group = (GroupData) SecurityUtils.deserialize(data.getData());
              final Log[] logs = (Log[]) group.getData();
              
              Continuation c = new StandardContinuation(parent) {
                int i = 0;
                Serializable aggregate = null;
                
                public void receiveResult(Object o) {
                  if (i < logs.length) {
                    if (logs[i] instanceof PostLog)
                      aggregate = ((PostLog) logs[i]).getAggregateHead();
                    
                    i++;                    
                    storeSigned(logs[i-1], logs[i-1].getLocation(), System.currentTimeMillis(), GCPast.INFINITY_EXPIRATION, keyPair, mutablePast, this);
                  } else {
                    parent.receiveResult(aggregate);
                  }
                }
              };
              
              c.receiveResult(null);
            } catch (IOException ioe) {
              parent.receiveException(new StorageException("IOException thrown during log recovery: " + ioe));
            } catch (ClassNotFoundException cnfe) {
              parent.receiveException(new StorageException("ClassNotFoundException thrown during log recovery: " + cnfe));
            } catch (PostException pe) {
              parent.receiveException(pe);
            }
          }
        });
      }
    });
  }
  
  /**
   * Method which sets the aggregate head, if we are using a Aggregation as
   * the immutable past store.
   *
   * @param log The log to set the aggregate in
   */
  public void setAggregate(final PostLog log, Continuation command) {
    if (immutablePast instanceof AggregationImpl) {
      ((AggregationImpl) immutablePast).flush(new StandardContinuation(command) {
        public void receiveResult(Object o) {
          log.setAggregateHead(((AggregationImpl) immutablePast).getHandle());
          parent.receiveResult(Boolean.TRUE);
        }
      });
    } else {
      command.receiveResult(Boolean.TRUE);
    }    
  }
  
  /**
   * Stores a PostData in the PAST store by signing the content and
   * storing it at a well-known location. This method also includes
   * a timestamp, which dates this update.
   *
   * Once the data has been stored, the command.receiveResult() method
   * will be called with a SignedReference as the argument.
   *
   * @param data The data to store
   * @param location The location where to store the data
   * @param command The command to run once the store has completed.
   */
  public void storeSigned(PostData data, Id location, Continuation command) {
    storeSigned(data, location, System.currentTimeMillis(), GCPast.INFINITY_EXPIRATION, keyPair, mutablePast, command);
  }

  /**
   * Stores a PostData in the PAST store by signing the content and
   * storing it at a well-known location. This method also includes
   * a timestamp, which dates this update.
   *
   * Once the data has been stored, the command.receiveResult() method
   * will be called with a SignedReference as the argument.
   *
   * @param data The data to store
   * @param location The location where to store the data
   * @param command The command to run once the store has completed.
   */
  protected static void storeSigned(final PostData data, final Id location, long time, long expiration, KeyPair keyPair, Past past, Continuation command) {
    try {
      byte[] plainText = SecurityUtils.serialize(data);
      byte[] timestamp = SecurityUtils.getByteArray(time);
      
      SignedData sd = new SignedData(location, plainText, timestamp);
      sd.setSignature(SecurityUtils.sign(sd.getDataAndTimestamp(), keyPair.getPrivate()));
      
      Continuation result = new StandardContinuation(command) {
        public void receiveResult(Object o) {
          Boolean[] results = (Boolean[]) o;
          int failed = 0;
          
          for (int i=0; i<results.length; i++) 
            if ((results[i] == null) || (! results[i].booleanValue())) 
              failed++;
          
          if (failed <= results.length/2)        
            parent.receiveResult(data.buildSignedReference(location));
          else
            parent.receiveException(new IOException("Storage of signed data into PAST failed - had " + failed + "/" + results.length + " failures."));
        }
      };
      
      // Store the signed data in PAST 
      if (past instanceof GCPast)
        ((GCPast) past).insert(sd, expiration, result);
      else
        past.insert(sd, result);
    } catch (IOException e) {
      command.receiveException(e);
    }
  }

  /**
   * This method retrieves a previously-stored private-key signed
   * block from PAST.  This method also does all necessary verification
   * checks and fetches the content from multiple locations in order
   * to prevent version-rollback attacks.
   *
   * Once the data has been stored, the command.receiveResult() method
   * will be called with a PostData as the argument.
   *
   * @param location The location of the data
   * @param command The command to run once the store has completed.
   */
  public void retrieveAndVerifySigned(SignedReference reference, Continuation command) {
    retrieveAndVerifySigned(reference, keyPair.getPublic(), command);
  }

  /**
   * This method retrieves a previously-stored block from PAST which was
   * signed using the private key matching the given public key.
   * This method also does all necessary verification
   * checks and fetches the content from multiple locations in order
   * to prevent version-rollback attacks.
   *
   * Once the data has been stored, the command.receiveResult() method
   * will be called with a PostData as the argument.
   *
   * @param location The location of the data
   * @param publicKey The public key matching the private key used to sign the data
   * @param command The command to run once the store has completed.
   */
  public void retrieveAndVerifySigned(SignedReference reference, final PublicKey publicKey, Continuation command) {
    retrieveSigned(reference, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        if (verifySigned((PostData) o, publicKey))
          parent.receiveResult(o);
        else
          parent.receiveException(new SecurityException("Verification of SignedData failed."));
      }
    });
  }    

  /**
   * This method retrieves a previously-stored block from PAST which was
   * signed using the private key. THIS METHOD EXPLICITLY DOES NOT PERFORM
   * ANY VERIFICATION CHECKS ON THE DATA.  YOU MUST CALL verifySigned() IN
   * ORDER TO VERIFY THE DATA.  This is provided for the case where the
   * cooresponding key is located in the data.
   *
   * Once the data has been stored, the command.receiveResult() method
   * will be called with a PostData as the argument.
   *
   * @param location The location of the data
   * @param command The command to run once the store has completed.
   */
  public void retrieveSigned(final SignedReference reference, Continuation command) {
    mutablePast.lookupHandles(reference.getLocation(), mutablePast.getReplicationFactor()+1, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        try {
          PastContentHandle[] handles = (PastContentHandle[]) o;
          
          if (handles == null) 
            throw new StorageException("Signed data not found in PAST - null returned!");
          
          StorageServiceDataHandle handle = null;
          
          for (int i=0; i<handles.length; i++) {
            StorageServiceDataHandle thisH = (StorageServiceDataHandle) handles[i];
                        
            if ((thisH != null) && ((handle == null) || (thisH.getVersion() > handle.getVersion()))) 
              handle = thisH;
          }
          
          if (handle == null)
            throw new StorageException("Signed data not found in PAST - all handles were null!");
          
          mutablePast.fetch(handle, new StandardContinuation(parent) {
            public void receiveResult(Object o) {
              try {
                SignedData sd = (SignedData) o;
                
                if (sd == null)
                  throw new StorageException("Signed data not found in PAST - handle fetch returned null!");
                  
                Object data = SecurityUtils.deserialize(sd.getData());
                
                pendingVerification.put(data, sd);
                
                parent.receiveResult((PostData) data);
              } catch (IOException ioe) {
                parent.receiveException(new StorageException("IOException while retrieving data at " + reference.getLocation() + ": " + ioe));
              } catch (ClassNotFoundException cnfe) {
                parent.receiveException(new StorageException("ClassNotFoundException while retrieving data: " + cnfe));
              } catch (PostException pe) {
                parent.receiveException(pe);
              }
            }
          });
        } catch (PostException pe) {
          parent.receiveException(pe);
        }
      }
    });
  }

  /**
   * This method verifies a signed block of data with the given public key.
   *
   * @param location The location of the data
   * @return The data
   */
  public boolean verifySigned(PostData data, PublicKey key) {
    SignedData sd = (SignedData) pendingVerification.remove(data);
    return ((sd != null) && SecurityUtils.verify(sd.getDataAndTimestamp(), sd.getSignature(), key)); 
  }

  /**
   * Stores a PostData in the PAST storage system, in encrypted state,
   * and returns a pointer and key to the data object.
   *
   * This method first generates a random key, uses this key to encrypt
   * the data, and then stored the data under the key of it's content-hash.
   *
   * Once the data has been stored, the command.receiveResult() method
   * will be called with a SecureReference as the argument.
   *
   * @param data The data to store.
   * @param command The command to run once the store has completed.
   */
  public void storeSecure(final PostData data, Continuation command) {
    try {
      final byte[] key = SecurityUtils.generateKeySymmetric();
      byte[] plainText = SecurityUtils.serialize(data);
      byte[] cipherText = SecurityUtils.encryptSymmetric(plainText, key);
      byte[] loc = SecurityUtils.hash(cipherText);
      
      final Id location = factory.buildId(loc);
      
      SecureData sd = new SecureData(location, cipherText);
      
      Continuation result = new StandardContinuation(command) {
        public void receiveResult(Object o) {
          Boolean[] results = (Boolean[]) o;
          int failed = 0;
          
          for (int i=0; i<results.length; i++)
            if ((results[i] == null) || (! results[i].booleanValue())) 
              failed++;
          
          if (failed <= results.length/2)         
            parent.receiveResult(data.buildSecureReference(location, key));
          else 
            parent.receiveException(new IOException("Storage of secure data into PAST failed - had " + failed + "/" + results.length + " failures."));
        }
      };
      
      // Store the content hash data in PAST
      if (immutablePast instanceof GCPast) 
        ((GCPast) immutablePast).insert(sd, getTimeout(), result);
      else 
        immutablePast.insert(sd, result);
    } catch (IOException e) {
      command.receiveException(e);
    }
  }

  /**
   * This method retrieves a given SecureReference object from the
   * network. This method also performs the verification checks and
   * decryption necessary.
   *
   * Once the data has been stored, the command.receiveResult() method
   * will be called with a PostData as the argument.
   *
   * @param reference The reference to the PostDataObject
   * @param command The command to run once the store has completed.
   */
  public void retrieveSecure(final SecureReference reference, Continuation command) {
    immutablePast.lookup(reference.getLocation(), new StandardContinuation(command) {
      public void receiveResult(Object result) {
        try {
          SecureData sd = (SecureData) result;
          
          if (sd == null) 
            throw new StorageException("Secure data not found in PAST!");
          
          byte[] key = reference.getKey();
          byte[] cipherText = sd.getData();
          byte[] plainText = SecurityUtils.decryptSymmetric(cipherText, key);
          Object data = SecurityUtils.deserialize(plainText);
          
          // Verify hash(cipher) == location
          if (! Arrays.equals(SecurityUtils.hash(cipherText), reference.getLocation().toByteArray()))
            throw new StorageException("Hash of cipher text does not match location for secure data.");
          
          parent.receiveResult((PostData) data);
        } catch (ClassCastException cce) {
          parent.receiveException(new StorageException("ClassCastException while retrieving data: " + cce));
        } catch (IOException ioe) {
          parent.receiveException(new StorageException("IOException while retrieving data: " + ioe));
        } catch (ClassNotFoundException cnfe) {
          parent.receiveException(new StorageException("ClassNotFoundException while retrieving data: " + cnfe));
        } catch (PostException pe) {
          parent.receiveException(pe);
        }
      }
    });
  }
  
  /**
   * This method "refreshes" a list of SecureReferences, which ensures that
   * all of the referenced objects are not collected by the underlying store.
   * This method should be invoked upon objects which the user is interested in, 
   * but the user did not create (i.e. The parts of an email the user has recevied).
   *
   * @param references The references to refresh
   * @param command The command to run once done
   */
  public void refreshSecure(SecureReference[] references, Continuation command) {
    if (immutablePast instanceof GCPast) {
      Id[] ids = new Id[references.length];
      
      for (int i=0; i<ids.length; i++)
        ids[i] = references[i].getLocation();
      
      ((GCPast) immutablePast).refresh(ids, getTimeout(), command);
    } else {
      command.receiveResult(Boolean.TRUE);
    }
  }
}
