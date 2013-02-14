/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
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
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.aggregation.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.p2p.past.rawserialization.*;
import rice.p2p.glacier.VersioningPast;
import rice.p2p.glacier.v2.GlacierContentHandle;
import rice.p2p.util.*;

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
@SuppressWarnings("unchecked")
public class StorageService {
  
  // the maximum size of a content-hash object
  public static int MAX_CONTENT_HASH_SIZE = 1000000;
  
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
   * The endpoint 
   */
  private Endpoint endpoint;

  /**
   * Lifetime of log head backups
   */
  private long BACKUP_LIFETIME = 1000 * 60 * 60 * 24 * 7;
  
  Environment environment;
  
  Logger logger;
  
  /**
   * Contructs a StorageService given a PAST to run on top of.
   *
   * @param past The PAST service to use.
   * @param credentials Credentials to use to store data.
   * @param keyPair The keypair to sign/verify data with
   */
  public StorageService(Endpoint endpoint, PostEntityAddress address, Past immutablePast, Past mutablePast, IdFactory factory, KeyPair keyPair, long timeoutInterval) {
    this.environment = endpoint.getEnvironment();
    logger = environment.getLogManager().getLogger(StorageService.class, null);    
    this.entity = address;
    this.immutablePast = immutablePast;
    
    this.mutablePast = mutablePast;
    
    PastContentDeserializer pcd = new JavaPastContentDeserializer() {
      public PastContent deserializePastContent(InputBuffer buf, Endpoint endpoint, short contentType) throws IOException {
        switch(contentType) {
          case ContentHashData.TYPE:
            return new ContentHashData(buf, endpoint);
          case SecureData.TYPE:
            return new SecureData(buf, endpoint);
          case SignedData.TYPE:
            return new SignedData(buf, endpoint);
        }
        return super.deserializePastContent(buf, endpoint, contentType);
      };
    };
    PastContentHandleDeserializer pchd = new JavaPastContentHandleDeserializer() {
      
      public PastContentHandle deserializePastContentHandle(InputBuffer buf, Endpoint endpoint,
          short contentType) throws IOException {
        switch(contentType) {
          case StorageServiceDataHandle.TYPE:
            return new StorageServiceDataHandle(buf, endpoint);
        }
        return super.deserializePastContentHandle(buf, endpoint, contentType);
      }    
    };

    immutablePast.setContentHandleDeserializer(pchd);
    immutablePast.setContentDeserializer(pcd);
    mutablePast.setContentHandleDeserializer(pchd);
    mutablePast.setContentDeserializer(pcd);
    
    this.keyPair = keyPair;
    this.factory = factory;
    this.timeoutInterval = timeoutInterval;
    this.endpoint = endpoint;
    
    pendingVerification = new Hashtable();
  }

  public Id getRandomNodeId() {
    byte[] data = new byte[20];
    environment.getRandomSource().nextBytes(data);
    
    return factory.buildId(data);
  }
  
  /**
   * Internal method which returns what the timeout should be for an
   * object inserted now.  Basically, does environment.getTimeSource().currentTimeMillis() +
   * timeoutInterval.
   *
   * @return The default timeout period for an object inserted now
   */
  protected long getTimeout() {
    return environment.getTimeSource().currentTimeMillis() + timeoutInterval;
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
  public void storeContentHash(final PostData data, final Continuation command) {
    endpoint.process(new Executable() {
      public Object execute() {
        try {
          return SecurityUtils.serialize(data);
        } catch (IOException e) {
          return e;
        }
      }
    }, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        if (o instanceof Exception) {
          parent.receiveException((Exception) o);
          return;
        }
        
        final byte[][] partitions = partition((byte[]) o);
        
        storeContentHashEntry(partitions[0], new StandardContinuation(parent) {
          protected int i=0;
          protected Id[] locations = new Id[partitions.length];
          protected byte[][] keys = new byte[partitions.length][];
          
          public void receiveResult(Object o) {
            locations[i] = (Id) ((Object[]) o)[0];
            keys[i] = (byte[]) ((Object[]) o)[1];
            i++;

            if (i < partitions.length) {
              storeContentHashEntry(partitions[i], this);
              return;
            } else {
              parent.receiveResult(data.buildContentHashReference(locations, keys));
            }
          }
        });
      }
    });
  }
  
  /**
   * Method which partitions a serialized object into acceptable size arrays - currently, 
   * it just splits the array into a bunch of arrays of size MAX_CONTENT_HASH_SIZE or
   * less.
   *
   * @param array the array
   * @return Splitted shaat
   */
  public byte[][] partition(byte[] array) {
    Vector result = new Vector();
    int offset = 0;
    
    while (offset < array.length) {
      byte[] next = new byte[(array.length - offset < MAX_CONTENT_HASH_SIZE ? array.length - offset : MAX_CONTENT_HASH_SIZE)];
      System.arraycopy(array, offset, next, 0, next.length);
      result.add(next);
      offset += next.length;
    }
    
    if (logger.level <= Logger.FINE) logger.log("PARTITION: Split " + array.length + " bytes into " + result.size() + " groups...");
    
    return (byte[][]) result.toArray(new byte[0][]);
  }
  
  /**
   * Performs the actual content hashing and insertion of a single content has
   * block.  Returns an array containing the Id and byte[] key of the inserted 
   * object.
   *
   * @param data The data to store.
   * @param command The command to run once the store has completed.
   */
  public void storeContentHashEntry(final byte[] plainText, final Continuation command) {
    endpoint.process(new Executable() {
      public Object execute() {
        return SecurityUtils.encryptSymmetric(plainText, SecurityUtils.hash(plainText));
      }
    }, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        final byte[] cipherText = (byte[]) o;
        final Id location = factory.buildId(SecurityUtils.hash(cipherText));
        final byte[] key = SecurityUtils.hash((byte[]) plainText);
        final ContentHashData chd = new ContentHashData(location, cipherText);
        
        immutablePast.lookupHandles(location, immutablePast.getReplicationFactor()+1, new StandardContinuation(parent) {
          public void receiveResult(Object o) {
            PastContentHandle[] handles = (PastContentHandle[]) o;
            
            // the object already exists - simply refresh and return a reference
            for (int i=0; i<handles.length; i++) {
              if (handles[i] != null) {
                refreshContentHash(new ContentHashReference[] {new ContentHashReference(new Id[] {location}, new byte[][] {key})}, new StandardContinuation(parent) {
                  public void receiveResult(Object o) {
                    parent.receiveResult(new Object[] {location, key});
                  }
                });
                
                return;
              }
            }
             
            // otherwise, we have to insert it ourselves
            Continuation result = new StandardContinuation(parent) {
              public void receiveResult(Object o) {
                Boolean[] results = (Boolean[]) o;
                int failed = 0;
                
                for (int i=0; i<results.length; i++) {
                  if ((results[i] == null) || (! results[i].booleanValue())) 
                    failed++;
                }
             
                if (failed > results.length/2) 
                  parent.receiveException(new IOException("Storage of content hash data into PAST failed - had " + failed + "/" + results.length + " failures."));

                // retrieve to make sure it got committed safely
                retrieveContentHashEntry(location, key, new StandardContinuation(parent) {
                  public void receiveResult(Object result) {
                    parent.receiveResult(new Object[] {location, key});
                  }
                  
                  public void receiveException(Exception e) {
                    if (logger.level <= Logger.WARNING) logger.log("******* CRYPTO ERROR (storeContentHashEntry) *******");
                    if (logger.level <= Logger.WARNING) logger.log("Received exception " + e + " while verifying inserted data!");
                    if (logger.level <= Logger.WARNING) logger.log("plaintext: " + MathUtils.toHex(plainText));
                    if (logger.level <= Logger.WARNING) logger.log("location: " + location);
                    if (logger.level <= Logger.WARNING) logger.log("key: "  +MathUtils.toHex(key));
                    if (logger.level <= Logger.WARNING) logger.logException("ciphertext: " + MathUtils.toHex(cipherText),e);
                    
                    parent.receiveException(new IOException("Storage of content hash data into PAST failed - could not decrypt after encryption"));                    
                  }
                });
              }
            };
            
            // Store the content hash data in PAST
            if (immutablePast instanceof GCPast) 
              ((GCPast) immutablePast).insert(chd, getTimeout(), result);
            else 
              immutablePast.insert(chd, result);
          }
        });
      }
    });
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
    retrieveContentHashEntry(reference.getLocations()[0], reference.getKeys()[0], new StandardContinuation(command) {
      protected int i = 0;
      protected byte[][] data = new byte[reference.getLocations().length][];
      protected int length = 0;
      
      public void receiveResult(Object o) {
        data[i] = (byte[]) o;
        length += data[i].length;
        i++;
        
        // first retrieve all of the entries
        if (i < reference.getLocations().length) {
          retrieveContentHashEntry(reference.getLocations()[i], reference.getKeys()[i], this);
          return;
        } else {
          // then actually put it all together and deserialize
          final byte[] plainText = new byte[length];
          int sofar = 0;
          
          for (int j=0; j<data.length; j++) {
            System.arraycopy(data[j], 0, plainText, sofar, data[j].length);
            sofar += data[j].length;
          }
          
          endpoint.process(new Executable() {
            public Object execute() {
              try {
                return (PostData) SecurityUtils.deserialize(plainText);
              } catch (ClassCastException cce) {
                return new StorageException("ClassCastException while retrieving data: " + cce);
              } catch (IOException ioe) {
                return new StorageException("IOException while retrieving data: " + ioe);
              } catch (ClassNotFoundException cnfe) {
                return new StorageException("ClassNotFoundException while retrieving data: " + cnfe);
              }
            }
          }, new StandardContinuation(parent) {
            public void receiveResult(Object o) {
              if (o instanceof PostData)
                parent.receiveResult(o);
              else
                parent.receiveException((Exception) o);
            }
          });          
        }
      }
    });
  }
  
  /**
   * This method retrieves a single content hash entry and verifies it.  The byte[]
   * data is returned to the caller
   *
   * @param reference The reference to the PostDataObject
   * @param command The command to run once the store has completed.
   */
  public void retrieveContentHashEntry(final Id location, final byte[] key, Continuation command) {
    immutablePast.lookup(location, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        ContentHashData chd = (ContentHashData) o;
        
        if (chd == null) {
          parent.receiveException(new StorageException(location, "Content hash data not found in PAST!"));
          return;
        }
        
        final byte[] cipherText = chd.getData();
        
        // Verify hash(cipher) == location
        if (! Arrays.equals(SecurityUtils.hash(cipherText), location.toByteArray())) {
          parent.receiveException(new StorageException(location, "Hash of cipher text does not match location."));
          return;
        } 
        
        endpoint.process(new Executable() {
          public Object execute() {
            return SecurityUtils.decryptSymmetric(cipherText, key);
          }
        }, new StandardContinuation(parent) {
          public void receiveResult(Object o) {
            final byte[] plainText = (byte[]) o;
                        
            // Verify hash(plain) == key
            if (! Arrays.equals(SecurityUtils.hash(plainText), key)) {
              parent.receiveException(new StorageException(location, "Hash of retrieved content does not match key."));
              return;
            } 
            
            // finally return the plaintext
            parent.receiveResult(plainText);
          }
        });
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
      HashSet idset = new HashSet();
      
      for (int i=0; i<references.length; i++)
        for (int j=0; j<references[i].getLocations().length; j++)
          idset.add(references[i].getLocations()[j]);
      
      Id[] ids = (Id[]) idset.toArray(new Id[0]);
      
      if (logger.level <= Logger.FINE) logger.log("CALLING REFRESH WITH " + ids.length + " OBJECTS!");
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
    long time = ((long) environment.getTimeSource().currentTimeMillis() / PostImpl.BACKUP_INTERVAL) * PostImpl.BACKUP_INTERVAL;
    storeSigned(new GroupData(logs), log.getLocation(), time, environment.getTimeSource().currentTimeMillis() + BACKUP_LIFETIME, keyPair, immutablePast, command);
  }
  
  /**
   * This method performs an emergency recovery of the logs by reinserting them into the
   * provided PAST store.
   *
   */
  public static void recoverLogs(final Id location, final long timestamp, final KeyPair keyPair, final Past immutablePast, final Past mutablePast, Continuation command, final Environment env, final Logger logger) {
    final long version = (timestamp / PostImpl.BACKUP_INTERVAL) * PostImpl.BACKUP_INTERVAL;
    if (logger.level <= Logger.FINE) logger.log(
        "COUNT: Timestamp is "+timestamp+", using version "+version);
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
          parent.receiveException(new StorageException(location, "All handles of log backup were null!"));
          return;
        }
        
        final Id location = handle.getId();
        
        immutablePast.fetch(handle, new StandardContinuation(parent) {
          public void receiveResult(Object o) {
            try {
              SignedData data = (SignedData) o;
              
              if (data == null) 
                throw new StorageException(location, "Log backup not found!");

              if (logger.level <= Logger.FINE) logger.log(
                  "COUNT: Log backup found!");

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
                    storeSigned(logs[i-1], logs[i-1].getLocation(), env.getTimeSource().currentTimeMillis(), GCPast.INFINITY_EXPIRATION, keyPair, mutablePast, this);
                  } else {
                    parent.receiveResult(aggregate);
                  }
                }
              };
              
              c.receiveResult(null);
            } catch (IOException ioe) {
              parent.receiveException(new StorageException(location, "IOException thrown during log recovery: " + ioe));
            } catch (ClassNotFoundException cnfe) {
              parent.receiveException(new StorageException(location, "ClassNotFoundException thrown during log recovery: " + cnfe));
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
  public void storeSigned(final PostData data, final Id location, Continuation command) {
    storeSigned(data, location, environment.getTimeSource().currentTimeMillis(), GCPast.INFINITY_EXPIRATION, keyPair, mutablePast, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        final SignedReference sr = (SignedReference) o;

        retrieveAndVerifySigned(sr, keyPair.getPublic(), new StandardContinuation(parent) {
          public void receiveResult(Object o) {
            parent.receiveResult(sr);
          }
          
          public void receiveException(Exception e) {
            if (e instanceof SecurityException) {
              ByteArrayOutputStream xxx = new ByteArrayOutputStream();
              ObjectOutputStream oo;
              try {
                oo = new ObjectOutputStream(xxx);
                oo.writeObject(data);
              } catch (IOException e1) {
                // do nothing
              }
              
              if (logger.level <= Logger.WARNING) logger.log("******* CRYPTO ERROR (storeSigned) *******");
              if (logger.level <= Logger.WARNING) logger.log("data: "+MathUtils.toHex(xxx.toByteArray()));
              if (logger.level <= Logger.WARNING) logger.log("location: "+location);
              if (logger.level <= Logger.WARNING) logger.log("public key: "+keyPair.getPublic());
              if (logger.level <= Logger.WARNING) logger.log("private key: "+keyPair.getPrivate());
              if (logger.level <= Logger.WARNING) logger.log("signed referece: "+sr);
              if (logger.level <= Logger.WARNING) logger.logException("stack trace:",e);
              parent.receiveException(new IOException("Storage of singed data into PAST failed - could not verify"));
            } else {
              parent.receiveException(e);
            }
          }
        });
      }
    });
  }

  /**
   * Stores a PostData in the PAST store by signing the content and
   * storing it at a well-known location. This method also includes
   * a timestamp, which dates this update.
   *
   * Once the data has been stored, the command.receiveResult() method
   * will be called with a SignedReference as the argument.
   * 
   * Calling this method does not verify the signed contents after storage.
   * If you need verification you will have to call retrieveAndVerifySigned
   * yourself.
   *
   * @param data The data to store
   * @param location The location where to store the data
   * @param command The command to run once the store has completed.
   */
  protected static void storeSigned(final PostData data, final Id location, long time, long expiration, final KeyPair keyPair, Past past, Continuation command) {
    try {
//      System.out.println("StorageService.storeSigned("+data+")");
      byte[] plainText = SecurityUtils.serialize(data);
      byte[] timestamp = MathUtils.longToByteArray(time);
      
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
            throw new StorageException(reference.getLocation(), "Signed data not found in PAST - null returned!");
          
          StorageServiceDataHandle handle = null;
          
          for (int i=0; i<handles.length; i++) {
            StorageServiceDataHandle thisH = (StorageServiceDataHandle) handles[i];
                        
            if ((thisH != null) && ((handle == null) || (thisH.getVersion() > handle.getVersion()))) 
              handle = thisH;
          }
          
          if (handle == null)
            throw new StorageException(reference.getLocation(), "Signed data not found in PAST - all handles were null!");
          
          mutablePast.fetch(handle, new StandardContinuation(parent) {
            public void receiveResult(Object o) {
              try {
                SignedData sd = (SignedData) o;
                
                if (sd == null)
                  throw new StorageException(reference.getLocation(), "Signed data not found in PAST - handle fetch returned null!");
                  
                Object data = SecurityUtils.deserialize(sd.getData());
                
                pendingVerification.put(data, sd);
                
                parent.receiveResult((PostData) data);
              } catch (IOException ioe) {
                parent.receiveException(new StorageException(reference.getLocation(), "IOException while retrieving data: " + ioe));
              } catch (ClassNotFoundException cnfe) {
                parent.receiveException(new StorageException(reference.getLocation(), "ClassNotFoundException while retrieving data: " + cnfe));
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
   * This method retrieves a previously-stored block from PAST which was
   * signed using the private key. THIS METHOD EXPLICITLY DOES NOT PERFORM
   * ANY VERIFICATION CHECKS ON THE DATA.  YOU MUST CALL verifySigned() IN
   * ORDER TO VERIFY THE DATA.  This is provided for the case where the
   * cooresponding key is located in the data.
   *
   * Once the data has been stored, the command.receiveResult() method
   * will be called with an array of PostData and Exceptions as the argument.
   *
   * @param location The location of the data
   * @param command The command to run once the store has completed.
   */
  public void retrieveAllSigned(final SignedReference reference, Continuation command) {
    mutablePast.lookupHandles(reference.getLocation(), mutablePast.getReplicationFactor() + 1, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        try {
          PastContentHandle[] handles = (PastContentHandle[]) o;

          if (handles == null) throw new StorageException(reference.getLocation(), "Signed data not found in PAST - null returned!");

          if (logger.level <= Logger.FINEST) logger.log("retrieveAllSigned got "+handles.length+" handles");
          
          Continuation afterFetch = new StandardContinuation(parent) {

            public void receiveResult(Object result) {
              Object[] results = (Object[]) result;
              Object[] data = new Object[results.length];

              for (int i = 0; i < results.length; i++) {
                if (logger.level <= Logger.FINEST) logger.log("retrieveAllSigned fetched data "+i+" is "+results[i]);
                SignedData sd = (SignedData)results[i];
                try {
                  if (sd != null) {
                    data[i] = SecurityUtils.deserialize(sd.getData());
                    pendingVerification.put(data[i], sd);
                  } else {
                    data[i] = null;
                  }
                } catch (IOException ioe) {
                  data[i] = new StorageException(reference.getLocation(), "IOException while retrieving data at " + reference.getLocation() + ": " + ioe);
                } catch (ClassNotFoundException cnfe) {
                  data[i] = new StorageException(reference.getLocation(), "ClassNotFoundException while retrieving data: " + cnfe);
                }
                if (logger.level <= Logger.FINEST) logger.log("retrieveAllSigned decoded data "+i+" is "+data[i]);
              }

              parent.receiveResult(data);
            }
          };

          MultiContinuation mc = new MultiContinuation(afterFetch, handles.length);

          for (int i = 0; i < handles.length; i++) {
            if (logger.level <= Logger.FINEST) logger.log("retrieveAllSigned handle "+i+" is "+handles[i]);
            if (handles[i] != null) {
              mutablePast.fetch(handles[i], mc.getSubContinuation(i));
            } else {
              mc.getSubContinuation(i).receiveResult(null);
            }
          }
        } catch (PostException pe) {
          parent.receiveException(pe);
        }
      }
    });
  }

  
  /**
   * This method verifies a signed block of data with the given public key.
   *
   * @param data The data
   * @param key The public key to verify the data against
   * @return Whether the key matches the data
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
      final byte[] plainText = SecurityUtils.serialize(data);
      final byte[] cipherText = SecurityUtils.encryptSymmetric(plainText, key);
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
          
          if (failed > results.length/2)         
            parent.receiveException(new IOException("Storage of secure data into PAST failed - had " + failed + "/" + results.length + " failures."));

          final SecureReference sr = data.buildSecureReference(location, key);
          
          retrieveSecure(sr, new StandardContinuation(parent) {
            public void receiveResult(Object o) {
              parent.receiveResult(sr);
            }
            
            public void receiveException(Exception e) {
              // XXX this is kind of stupid; retrieveSecure just deserialized it
              // and we reserialze just to compare
              if (logger.level <= Logger.WARNING) logger.log("******* CRYPTO ERROR (storeSecure) *******");
              if (logger.level <= Logger.WARNING) logger.log("Received exception " + e + " verifying inserted data.");
              if (logger.level <= Logger.WARNING) logger.log("plaintext: " + MathUtils.toHex(plainText));
              if (logger.level <= Logger.WARNING) logger.log("location: " + location);
              if (logger.level <= Logger.WARNING) logger.log("key: "+ MathUtils.toHex(key));
              if (logger.level <= Logger.WARNING) logger.logException("ciphertext: " + MathUtils.toHex(cipherText),e);
              
              parent.receiveException(new IOException("Storage of secure data into PAST failed - could not recover data"));
            }
          });
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
            throw new StorageException(reference.getLocation(), "Secure data not found in PAST!");
          
          byte[] key = reference.getKey();
          byte[] cipherText = sd.getData();

          // Verify hash(cipher) == location
          if (! Arrays.equals(SecurityUtils.hash(cipherText), reference.getLocation().toByteArray()))
            throw new StorageException(reference.getLocation(), "Hash of cipher text does not match location for secure data.");
          
          byte[] plainText = SecurityUtils.decryptSymmetric(cipherText, key);
          
          parent.receiveResult((PostData) SecurityUtils.deserialize(plainText));
        } catch (ClassCastException cce) {
          parent.receiveException(new StorageException(reference.getLocation(), "ClassCastException while retrieving data: " + cce));
        } catch (IOException ioe) {
          parent.receiveException(new StorageException(reference.getLocation(), "IOException while retrieving data: " + ioe));
        } catch (ClassNotFoundException cnfe) {
          parent.receiveException(new StorageException(reference.getLocation(), "ClassNotFoundException while retrieving data: " + cnfe));
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
