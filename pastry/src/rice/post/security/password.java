package rice.post.security;

public class password {
  public static native String getPassword();

  static {
    System.loadLibrary("password");
  }
}