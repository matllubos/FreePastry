package rice.post.email;

/**
 * This interface defines the methods used by the {@link EmaiService} to notify other
 * objects of events that occur in it.
 * 
 * @author Derek Ruths
 */
public interface EmailServiceListener {

  /**
   * This method is called on this object when an email is received by an
   * EmailService object.
   * 
   * @param email is the email that was received.
   */
  public void messageReceived(Email email);
}
