package rice.p2p.glacier.v2;

import rice.environment.Environment;
import rice.p2p.commonapi.IdRange;

public class GlacierStatistics {
  public int messagesSentByTag[];
  public int pendingRequests;
  public int numNeighbors;
  public int numFragments;
  public int numContinuations;
  public int numObjectsInTrash;
  public int activeFetches;
  public IdRange responsibleRange;
  public long fragmentStorageSize;
  public long trashStorageSize;
  public long tbegin;
  public long bucketMin;
  public long bucketMax;
  public long bucketConsumed;
  public long bucketTokensPerSecond;
  public long bucketMaxBurstSize;
  public Environment environment;
  
  public GlacierStatistics(int numTags, Environment env) {
    this.environment = env;
    this.messagesSentByTag = new int[numTags];
    this.pendingRequests = 0;
    this.numNeighbors = 0;
    this.numFragments = 0;
    this.numContinuations = 0;
    this.numObjectsInTrash = 0;
    this.fragmentStorageSize = 0;
    this.trashStorageSize = 0;
    this.activeFetches = 0;
    this.tbegin = env.getTimeSource().currentTimeMillis();
    this.bucketMin = 0;
    this.bucketMax = 0;
    this.bucketConsumed = 0;
  };
  
  public void dump() {
    System.out.println("@L.ME free="+Runtime.getRuntime().freeMemory()+" max="+Runtime.getRuntime().maxMemory()+" total="+Runtime.getRuntime().totalMemory());
    System.out.println("@L.GL interval="+tbegin+"-"+environment.getTimeSource().currentTimeMillis()+" range="+responsibleRange);
    System.out.println("@L.GL   neighbors="+numNeighbors+" fragments="+numFragments+" trash="+numObjectsInTrash);
    System.out.println("@L.GL   continuations="+numContinuations+" pending="+pendingRequests);
    System.out.println("@L.GL   fragSizeBytes="+fragmentStorageSize+" trashSizeBytes="+trashStorageSize);
    System.out.println("@L.GL   activeFetches="+activeFetches+" bucketMin="+bucketMin+" bucketMax="+bucketMax);
    System.out.println("@L.GL   bucketConsumed="+bucketConsumed);
    System.out.print("@L.GL   byTag=");
    for (int i=0; i<messagesSentByTag.length; i++)
      System.out.print(messagesSentByTag[i]+" ");
    System.out.println();
  }
};
