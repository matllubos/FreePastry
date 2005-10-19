package rice.email.proxy.dns;

public interface DnsService{
  
  public String[] lookup(String host);
}