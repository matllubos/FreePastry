package rice.email.proxy;

public class password {
  public static native String getPassword();

  static {
    System.loadLibrary("password");
  }
}