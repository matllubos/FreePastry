package rice.past;

import rice.pastry.security.Credentials;
import rice.pastry.NodeId;
import rice.storage.StorageObject;

import ObjectWeb.Persistence.Persistable;

/**
 * @(#) PASTService.java
 *
 * This interface is exported by PAST for any applications or components
 * which need to store replicated copies of documents on the Pastry
 * network.
 *
 * @version $Id$
 * @author Charles Reis
 */
public interface PASTService {
  
  /**
   * Inserts a file into the remote PAST storage system, using the given
   * file ID.
   * @param fileId NodeId to use as a handle for the file
   * @param file File to store in PAST
   * @param authorCred Credentials of author of file
   * @return true if the file was successfully stored
   */
  public boolean insert(NodeId fileId, Persistable file, Credentials authorCred);
  
  /**
   * Appends an update to an existing file in the PAST storage system.
   * @param fileId Handle of original file
   * @param update Update to the file stored at fileId
   * @return true if the original file exists and was updated, false otherwise
   */
  public boolean append(NodeId fileId, Persistable update);
  
  /**
   * Locates and returns the file and all updates associated with fileId.
   * @param fileId Handle of original file
   * @return StorageObject with Persistable file and all updates,
   * or null if fileId not found.
   */
  public StorageObject lookup(NodeId fileId);
  
  /**
   * Reclaims the space used by the file with handle fileId,
   * effectively deleting it.
   * @param fileId Handle of original file
   * @param authorCred Credentials of user requesting the reclaim
   * @return true if the file was found and deleted, false otherwise
   */
  public boolean reclaim(NodeId fileId, Credentials authorCred);
}