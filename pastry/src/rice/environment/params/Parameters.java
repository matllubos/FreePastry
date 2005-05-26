/*
 * Created on May 26, 2005
 */
package rice.environment.params;

import java.net.*;

/**
 * 
 * 
 * @author Jeff Hoye
 */
public interface Parameters {
  // getters
  public int getInt(String paramName, int defaultVal);
  public double getDouble(String paramName, double defaultVal);
  public float getFloat(String paramName, float defaultVal);
  public boolean getBoolean(String paramName, boolean defaultVal);
  
  /**
   * String format is dnsname
   * ex: "computer.school.edu"
   * @param paramName
   * @param defaultVal should look like "computer.school.edu";
   * @return
   * @throws UnknownHostException
   */
  public InetAddress getInetAddress(String paramName, String defaultVal) throws UnknownHostException;
  public InetAddress getInetAddress(String paramName, InetAddress defaultVal) throws UnknownHostException;
  
  
  /**
   * String format is name:port
   * ex: "computer.school.edu:1984"
   * @param paramName
   * @param defaultVal should look like "computer.school.edu:1984";
   * @return
   */
  public InetSocketAddress getInetSokcetAddress(String paramName, String defaultVal);
  public InetSocketAddress getInetSokcetAddress(String paramName, InetSocketAddress defaultVal);
  
  /**
   * String format is comma seperated.
   * ex: "computer.school.edu:1984,computer2.school.edu:1984,computer.school.edu:1985"
   * @param paramName
   * @param defaultVal should look like "computer.school.edu:1984";
   * @return
   */
  public InetSocketAddress[] getInetSokcetAddresses(String paramName, String defaultVal);
  public InetSocketAddress[] getInetSokcetAddresses(String paramName, InetSocketAddress[] defaultVal);
  
  // setters
  public void setInt(String paramName, int val);
  public void setDouble(String paramName, double val);
  public void setFloat(String paramName, float val);
  public void setBoolean(String paramName, boolean val);
  public void setInetAddress(String paramName, InetAddress val);
  public void setInetSokcetAddress(String paramName, InetSocketAddress val);
  public void setInetSokcetAddresses(String paramName, InetSocketAddress[] val);
}
