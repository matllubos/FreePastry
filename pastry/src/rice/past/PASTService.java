package rice.past;

import rice.storage.StorageManager;

/**
 * @(#) PASTService.java
 *
 * This interface is exported by PAST for any applications or components
 * which need to store replicated copies of documents on the Pastry
 * network.  It provides the same interface as the local StorageManager.
 *
 * @version $Id$
 * @author Charles Reis
 */
public interface PASTService extends StorageManager {
}