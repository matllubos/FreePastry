package rice.post.storage;

import java.io.*;

/**
 * This interface is designed to serve as an abstraction of a
 * data object stored in Post. This object will be stored
 * in an encrypted state at a location in the network.  Users
 * can access this object by having a copy of the cooresponding
 * PostDataReference object, which contains the location and
 * key of this object.
 */
public interface PostData extends Serializable {

}
