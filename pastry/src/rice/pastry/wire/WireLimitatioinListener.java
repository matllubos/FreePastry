/*
 * Created on Jan 14, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package rice.pastry.wire;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public interface WireLimitatioinListener {
  void resourcesRunningShort(int numFileDescriptorsUsed, int approxNumFileDescriptorsAvailable);
  void resourcesFine(int numFileDescriptorsUsed, int approxNumFileDescriptorsAvailable);
}
