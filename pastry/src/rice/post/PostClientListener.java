package rice.post;

/**
 * This interface defines the methods used by the {@link PostClient} to notify other
 * objects of events that occur in it.
 * 
 * @author Derek Ruths
 */
public interface PostClientListener {

    /**
     * This method is called on this object when an email is received by a PostClient object.
     * 
     * @param pc is the client that received the email.
     * @param email is the email that was received.
     */
    public void messageReceived(PostClient pc, Email email);
}
