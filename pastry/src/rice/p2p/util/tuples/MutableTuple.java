package rice.p2p.util.tuples;

public class MutableTuple<A,B> extends Tuple<A,B> {

  public MutableTuple(A a, B b) {
    super(a, b);
  }
  
  public MutableTuple() {
    super(null, null);
  }
  
  public void set(A a, B b) {
    setA(a);
    setB(b);
  } 
  
  public void setA(A a) {
    this.a = a;
  }

  public void setB(B b) {
    this.b = b;
  }

}
