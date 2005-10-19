package rice.email.proxy.imap;

public interface Quittable
{
	/**
	 * Flags something (probably an ImapHandler)
	 * as quitting.  That does not mean that the
	 * current transactions (IMAP commands) will
	 * be interrupted, but it does mean that no
	 * more transactions will be accepted.
	 */
    public void quit();
}