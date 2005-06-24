package rice.p2p.glacier.v2;

import rice.environment.Environment;
import rice.environment.logging.Logger;
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
  
  public void dump(Logger logger) {
    String s = "";
    s+="@L.ME free="+Runtime.getRuntime().freeMemory()+" max="+Runtime.getRuntime().maxMemory()+" total="+Runtime.getRuntime().totalMemory()+"\n";
    s+="@L.GL interval="+tbegin+"-"+environment.getTimeSource().currentTimeMillis()+" range="+responsibleRange+"\n";
    s+="@L.GL   neighbors="+numNeighbors+" fragments="+numFragments+" trash="+numObjectsInTrash+"\n";
    s+="@L.GL   continuations="+numContinuations+" pending="+pendingRequests+"\n";
    s+="@L.GL   fragSizeBytes="+fragmentStorageSize+" trashSizeBytes="+trashStorageSize+"\n";
    s+="@L.GL   activeFetches="+activeFetches+" bucketMin="+bucketMin+" bucketMax="+bucketMax+"\n";
    s+="@L.GL   bucketConsumed="+bucketConsumed+"\n";
    s+="@L.GL   byTag=";
    for (int i=0; i<messagesSentByTag.length; i++)
      s+=messagesSentByTag[i]+" ";
    s+="\n";
    logger.log(Logger.INFO, s);
  }
};
