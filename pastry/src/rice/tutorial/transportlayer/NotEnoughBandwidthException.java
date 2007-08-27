package rice.tutorial.transportlayer;

import java.io.IOException;

public class NotEnoughBandwidthException extends IOException {
  long bucketSize;
  int attemptedToWrite;
  
  public NotEnoughBandwidthException(long bucketSize, int attemptedToWrite) {
    this.bucketSize = bucketSize;
    this.attemptedToWrite = attemptedToWrite;
  }

  /**
   * @return The amount allowed to write (right now).
   */
  public long getAmountAllowedToWrite() {
    return bucketSize;
  }
  
  /**
   * @return The amount we attempted to write.
   */
  public int getAttemptedToWrite() {
    return attemptedToWrite;
  }
}
