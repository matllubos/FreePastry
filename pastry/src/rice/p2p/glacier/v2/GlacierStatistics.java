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
  
  public GlacierStatistics(int numTags) {
    this.messagesSentByTag = new int[numTags];
    this.pendingRequests = 0;
    this.numNeighbors = 0;
    this.numFragments = 0;
    this.numContinuations = 0;
    this.numObjectsInTrash = 0;
  };
  
  public String toString() {
    String result = "";
    
    for (int i=0; i<messagesSentByTag.length; i++)
      result = result + "tag"+i+"="+messagesSentByTag[i]+" ";
      
    result = result + " neigh=" + numNeighbors + " frag=" + numFragments +
             " cont=" + numContinuations + " trash=" + numObjectsInTrash;
             
    return result;
  }
};
