package rice.p2p.glacier.v2;

import rice.p2p.commonapi.IdRange;

public class GlacierStatistics {
  public int messagesSentByTag[];
  public int pendingRequests;
  public int numNeighbors;
  public int numFragments;
  public int numContinuations;
  public int numObjectsInTrash;
  public IdRange responsibleRange;
  public long fragmentStorageSize;
  public long trashStorageSize;
  public long tbegin;
  
  public GlacierStatistics(int numTags) {
    this.messagesSentByTag = new int[numTags];
    this.pendingRequests = 0;
    this.numNeighbors = 0;
    this.numFragments = 0;
    this.numContinuations = 0;
    this.numObjectsInTrash = 0;
    this.fragmentStorageSize = 0;
    this.trashStorageSize = 0;
    this.tbegin = System.currentTimeMillis();
  };
  
  public void dump() {
    System.out.println("@L.ME free="+Runtime.getRuntime().freeMemory()+" max="+Runtime.getRuntime().maxMemory()+" total="+Runtime.getRuntime().totalMemory());
    System.out.println("@L.GL interval="+tbegin+"-"+System.currentTimeMillis()+" range="+responsibleRange);
    System.out.println("@L.GL   neighbors="+numNeighbors+" fragments="+numFragments+" trash="+numObjectsInTrash);
    System.out.println("@L.GL   continuations="+numContinuations+" pending="+pendingRequests);
    System.out.println("@L.GL   fragSizeBytes="+fragmentStorageSize+" trashSizeBytes="+trashStorageSize);
    System.out.print("@L.GL   byTag=");
    for (int i=0; i<messagesSentByTag.length; i++)
      System.out.print(messagesSentByTag[i]+" ");
    System.out.println();
  }
};
