/*
 * Created on May 26, 2005
 */
package rice.environment.params;

import java.io.IOException;
import java.net.*;

/**
 * 
 * 
 * @author Jeff Hoye
 */
public interface Parameters {
  // 
  public void remove(String name);  
  public boolean contains(String name);
  public void store() throws IOException;
  
  // getters
  public String getString(String paramName);
  public String[] getStringArray(String paramName);
  public int getInt(String paramName);
  public double getDouble(String paramName);
  public float getFloat(String paramName);
  public long getLong(String paramName);
  public boolean getBoolean(String paramName);
  
  /**
   * String format is dnsname
   * ex: "computer.school.edu"
   * @param paramName
   * @return
   * @throws UnknownHostException
   */
  public InetAddress getInetAddress(String paramName) throws UnknownHostException;
  
  
  /**
   * String format is name:port
   * ex: "computer.school.edu:1984"
   * @param paramName
   * @return
   */
  public InetSocketAddress getInetSocketAddress(String paramName) throws UnknownHostException;
  
  /**
   * String format is comma seperated.
   * ex: "computer.school.edu:1984,computer2.school.edu:1984,computer.school.edu:1985"
   * @param paramName
   * @return
   */
  public InetSocketAddress[] getInetSocketAddressArray(String paramName) throws UnknownHostException;
  
  // setters
  public void setString(String paramName, String val);
  public void setStringArray(String paramName, String[] val);
  public void setInt(String paramName, int val);
  public void setDouble(String paramName, double val);
  public void setFloat(String paramName, float val);
  public void setLong(String paramName, long val);
  public void setBoolean(String paramName, boolean val);
  public void setInetAddress(String paramName, InetAddress val);
  public void setInetSocketAddress(String paramName, InetSocketAddress val);
  public void setInetSocketAddressArray(String paramName, InetSocketAddress[] val);
  
  public void addChangeListener(ParameterChangeListener p);
  public void removeChangeListener(ParameterChangeListener p);
}
