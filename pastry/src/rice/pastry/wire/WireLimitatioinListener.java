package rice.pastry.wire;

/**
 * This is an interface to notify user code that the FileDescriptors
 * for the system are being saturated.  It is not currently used.
 * 
 * @author Jeff Hoye
 */
public interface WireLimitatioinListener {
  void resourcesRunningShort(int numFileDescriptorsUsed, int approxNumFileDescriptorsAvailable);
  void resourcesFine(int numFileDescriptorsUsed, int approxNumFileDescriptorsAvailable);
}
