package rice.p2p.aggregation;

import java.util.Arrays;

public class AggregationStatistics {
  public final long granularity;
  public int numObjectsTotal;
  public int numObjectsAlive;
  public int numAggregatesTotal;
  public int numPointerArrays;
  public int criticalAggregates;
  public int orphanedAggregates;
  public int[] objectLifetimeHisto;
  public int[] aggregateLifetimeHisto;
  public long totalObjectsSize;
  public long liveObjectsSize;
  public long time;
  
  public AggregationStatistics(int histoLength, long granularityArg) {
    numObjectsTotal = 0;
    numObjectsAlive = 0;
    numAggregatesTotal = 0;
    numPointerArrays = 0;
    totalObjectsSize = 0;
    liveObjectsSize = 0;
    granularity = granularityArg;
    objectLifetimeHisto = new int[histoLength];
    Arrays.fill(objectLifetimeHisto, 0);
    aggregateLifetimeHisto = new int[histoLength];
    Arrays.fill(aggregateLifetimeHisto, 0);
    time = System.currentTimeMillis();
  }
  
  public void dump() {
    System.out.println("@L.AG interval="+time+"-"+System.currentTimeMillis()+" granularity="+granularity);
    System.out.println("@L.AG   objsTotal="+numObjectsTotal+" objsAlive="+numObjectsAlive);
    System.out.println("@L.AG   objBytesTotal="+totalObjectsSize+" objBytesAlive="+liveObjectsSize);
    System.out.println("@L.AG   aggrTotal="+numAggregatesTotal+" ptrArrays="+numPointerArrays+" critical="+criticalAggregates+" orphaned="+orphanedAggregates);
  }
}
