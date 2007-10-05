package rice.p2p.util.tuples;

public class Tuple<A,B> {
  protected A a;
  protected B b;
  
  public Tuple(A a, B b) {
    this.a = a;
    this.b = b;
  }
  
  public A a() {
    return a;
  }
  
  public B b() {
    return b;
  }
  
  /**
   * Gotta handle null values
   */
  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Tuple) {  
      Tuple<A,B> that = (Tuple<A,B>)obj;
      if (this.a == null) { 
        if (that.a != null) return false;        
      } else {
        if (that.a == null) return false;
        // we know this.a && that.a != null
        if (!this.a.equals(that.a)) return false;
      }
      
      if (this.b == null) { 
        if (that.b != null) return false;        
      } else {
        if (that.b == null) return false;
        // we know this.a && that.a != null
        if (!this.b.equals(that.b)) return false;
      }
      
      return true;
    }
    return false;
  }

  /**
   * Gotta handle null values.
   */
  @Override
  public int hashCode() {
    int hashA = 0;
    if (a != null) hashA = a.hashCode();
    int hashB = 0;
    if (b != null) hashB = b.hashCode();
    return hashA^hashB;
  }  
}
