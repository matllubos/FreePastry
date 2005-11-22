package rice.p2p.aggregation;

import java.util.Arrays;

import rice.environment.Environment;
import rice.environment.logging.Logger;

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
  private Environment environment;
  
  public AggregationStatistics(int histoLength, long granularityArg, Environment env) {
    this.environment = env;
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
    time = environment.getTimeSource().currentTimeMillis();
  }
  
  public void dump(Logger logger) {
    String s = "@L.AG interval="+time+"-"+environment.getTimeSource().currentTimeMillis()+" granularity="+granularity+"\n";
    s+="@L.AG   objsTotal="+numObjectsTotal+" objsAlive="+numObjectsAlive+"\n";
    s+="@L.AG   objBytesTotal="+totalObjectsSize+" objBytesAlive="+liveObjectsSize+"\n";
    s+="@L.AG   aggrTotal="+numAggregatesTotal+" ptrArrays="+numPointerArrays+" critical="+criticalAggregates+" orphaned="+orphanedAggregates;
    if (logger.level <= Logger.INFO) logger.log(s);
  }
}
