package rice.email.proxy.smtp.client;

public class SmtpProtocolException extends Exception {

  public SmtpProtocolException() {
    super();
  }

  public SmtpProtocolException(String s) {
    super(s);
  }

  public SmtpProtocolException(String s, Throwable t) {
    super(s, t);
  }

  public SmtpProtocolException(Throwable t) {
    super(t);
  }
}