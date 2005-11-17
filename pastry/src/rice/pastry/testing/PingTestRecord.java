package rice.pastry.testing;

import rice.pastry.direct.TestRecord;

/**
 * PingAddress
 * 
 * A performance test suite for pastry.
 * 
 * @version $Id$
 * 
 * @author Rongmei Zhang
 */

public class PingTestRecord extends TestRecord {
  private int nIndex;

  private int nHops[];

  private double fProb[];

  private double fHops;

  private double fDistance = 0;

  public PingTestRecord(int n, int k, int baseBitLength) {
    super(n, k);

    nIndex = (int) Math
        .ceil(Math.log(n) / Math.log(Math.pow(2, baseBitLength)));
    nIndex *= 3;
    nHops = new int[nIndex*2];
    fProb = new double[nIndex*2];
  }

  public void doneTest() {
    int i;
    //calculate averages ...
    long sum = 0;
    for (i = 0; i < nIndex; i++) {
      sum += nHops[i] * i;
    }
    fHops = ((double) sum) / nTests;
    fDistance = fDistance / nTests;

    for (i = 0; i < nIndex; i++) {
      fProb[i] = i * nHops[i] / ((double) sum);
    }
  }

  public void addHops(int index) {
    nHops[index]++;
  }

  public void addDistance(double rDistance) {
    fDistance += rDistance;
  }

  public double getAveHops() {
    return fHops;
  }

  public double getAveDistance() {
    return fDistance;
  }

  public double[] getProbability() {
    return fProb;
  }
}

