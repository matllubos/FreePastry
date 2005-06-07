package rice.pastry.routing;

import java.io.*;

/**
 * This is the options for a client to send messages.
 * 
 * @version $Id$
 * 
 * @author Andrew Ladd
 */

public class SendOptions implements Serializable {
  private boolean random;

  private boolean noShortCuts;

  private boolean shortestPath;

  private boolean allowMultipleHops;

  private boolean rerouteIfSuspected;

  public static final boolean defaultRandom = false;

  public static final boolean defaultNoShortCuts = true;

  public static final boolean defaultShortestPath = true;

  public static final boolean defaultAllowMultipleHops = true;

  public static final boolean defaultRerouteIfSuspected = true;

  /**
   * Constructor.
   */

  public SendOptions() {
    random = defaultRandom;
    noShortCuts = defaultNoShortCuts;
    shortestPath = defaultShortestPath;
    allowMultipleHops = defaultAllowMultipleHops;
    rerouteIfSuspected = defaultRerouteIfSuspected;
  }

  /**
   * Constructor.
   * 
   * @param random true if randomize the route
   * @param noShortCuts true if require each routing step to go to a node whose
   *          id matches in exactly one more digit
   * @param shortestPath true if require to go to the strictly nearest known
   *          node with appropriate node id
   * @param allowMultipleHops true if we allow multiple hops for this
   *          transmission, false otherwise.
   */

  public SendOptions(boolean random, boolean noShortCuts, boolean shortestPath,
      boolean allowMultipleHops, boolean rerouteIfSuspected) {
    this.random = random;
    this.noShortCuts = noShortCuts;
    this.shortestPath = shortestPath;
    this.allowMultipleHops = allowMultipleHops;
    this.rerouteIfSuspected = rerouteIfSuspected;
  }

  /**
   * Returns whether randomizations on the route are allowed.
   * 
   * @return true if randomizations are allowed.
   */

  public boolean canRandom() {
    return random;
  }

  /**
   * Returns whether it is required for each routing step to go to a node whose
   * id matches in exactly one more digit.
   * 
   * @return true if it is required to go to a node whose id matches in exactly
   *         one more digit.
   */

  public boolean makeNoShortCuts() {
    return noShortCuts;
  }

  /**
   * Returns whether it is required to go to the strictly nearest known node
   * with appropriate node id.
   * 
   * @return true if it is required to go to the strictly nearest known node
   *         with appropriate node id.
   */

  public boolean requireShortestPath() {
    return shortestPath;
  }

  /**
   * Returns whether multiple hops are allowed during the transmission of this
   * message.
   * 
   * @return true if so, false otherwise.
   */

  public boolean multipleHopsAllowed() {
    return allowMultipleHops;
  }

  public void setMultipleHopsAllowed(boolean b) {
    allowMultipleHops = b;
  }

  public boolean rerouteIfSuspected() {
    return rerouteIfSuspected;
  }

  public void setRerouteIfSuspected(boolean b) {
    rerouteIfSuspected = b;
  }

  private void readObject(ObjectInputStream in) throws IOException,
      ClassNotFoundException {
    random = in.readBoolean();
    noShortCuts = in.readBoolean();
    shortestPath = in.readBoolean();
    allowMultipleHops = in.readBoolean();
  }

  private void writeObject(ObjectOutputStream out) throws IOException,
      ClassNotFoundException {
    out.writeBoolean(random);
    out.writeBoolean(noShortCuts);
    out.writeBoolean(shortestPath);
    out.writeBoolean(allowMultipleHops);
  }

}

