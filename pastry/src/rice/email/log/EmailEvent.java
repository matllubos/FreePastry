package rice.email.log;

import rice.email.*;

/**
 * Interface for all email events.  Requires a getEmail() method
 * to return 
 */
public interface EmailEvent {
  /**
   * Returns the email which this event references.
   *
   * @return The email referenced
   */
  public Email getEmail();
}
