package rice.p2p.glacier.v2;

public abstract class GlacierContinuation {
  
  protected int myUID;
  
  protected boolean terminated;
  
  abstract public void receiveResult(Object result);
    
  abstract public void receiveException(Exception exception);
  
  abstract public void timeoutExpired();
  
  abstract public long getTimeout();

  public void init() {
  }
  
  public void syncReceiveResult(Object result) {
    if (!terminated)
      receiveResult(result);
  }
      
  public void syncReceiveException(Exception exception) {
    if (!terminated)
      receiveException(exception);
  }  
  
  public void syncTimeoutExpired() {
    if (!terminated)
      timeoutExpired();
  }
  
  public void setup(int uid) {
    myUID = uid;
    terminated = false;
  }
  
  public int getMyUID() {
    return myUID;
  } 
  
  public boolean hasTerminated() {
    return terminated;
  }
  
  public void terminate() {
    terminated = true;
  }
  
  public String toString() {
    return "Unknown continuation #"+getMyUID();
  }
};
