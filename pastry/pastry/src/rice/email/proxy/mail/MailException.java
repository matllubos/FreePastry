package rice.email.proxy.mail;

public class MailException extends Exception {

	public MailException() {
		super();
	}

	public MailException(String s) {
		super(s);
	}

	public MailException(String s, Throwable t) {
		super(s, t);
	}

	public MailException(Throwable t) {
		super(t);
	}

}
