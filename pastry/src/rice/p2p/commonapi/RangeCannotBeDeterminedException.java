/*
 * Created on Apr 27, 2006
 */
package rice.p2p.commonapi;

/**
 * This exception is thrown when the underlieing Overlay cannot determine the 
 * requested id range.  For whatever reason, the local node does not have enough
 * information to generate the correct range.
 * @author Jeff Hoye
 */
public class RangeCannotBeDeterminedException extends RuntimeException {

  public RangeCannotBeDeterminedException() {
    super();
  }

  public RangeCannotBeDeterminedException(String arg0) {
    super(arg0);
  }

  public RangeCannotBeDeterminedException(Throwable arg0) {
    super(arg0);
  }

  public RangeCannotBeDeterminedException(String arg0, Throwable arg1) {
    super(arg0, arg1);
  }
}
