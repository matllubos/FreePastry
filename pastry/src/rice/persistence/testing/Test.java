
package rice.persistence.testing;

/*
 * @(#) StorageTest.java
 *
 * @author Ansley Post
 * @author Alan Mislove
 * 
 * @version $Id$
 */
import java.util.*;

import rice.*;
import rice.persistence.*;

/**
 * This class is a class which tests the Storage class
 * in the rice.persistence package.
 */
public abstract class Test {

  protected static final String SUCCESS = "SUCCESS";
  protected static final String FAILURE = "FAILURE";

  protected static final int PAD_SIZE = 60;

  public abstract void start();

  protected void sectionStart(String name) {
    System.out.println(name);
  }

  protected void sectionEnd() {
    System.out.println();
  }

  protected void stepStart(String name) {
    System.out.print(pad("  " + name));
  }

  protected void stepDone(String status) {
    System.out.println("[" + status + "]");
    if(status.equals(FAILURE))
       System.exit(0);
  }

  protected void stepDone(String status, String message) {
    System.out.println("[" + status + "]");
    System.out.println("    " + message);
    if(status.equals(FAILURE))
       System.exit(0);
  }

  protected void stepException(Exception e) {
    System.out.println();

    System.out.println("Exception " + e + " occurred during testing.");

    e.printStackTrace();
    System.exit(0);
  }

  private String pad(String start) {
    if (start.length() >= PAD_SIZE) {
      return start.substring(0, PAD_SIZE);
    } else {
      int spaceLength = PAD_SIZE - start.length();
      char[] spaces = new char[spaceLength];
      Arrays.fill(spaces, '.');

      return start.concat(new String(spaces));
    }
  }
}
