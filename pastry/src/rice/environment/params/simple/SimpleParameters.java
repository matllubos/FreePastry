/*
 * Created on May 26, 2005
 */
package rice.environment.params.simple;

import java.net.*;
import java.util.Properties;

import rice.environment.params.Parameters;

/**
 * Implementation of Parameters, based on / backed by a java.util.Properties
 * 
 * Typical use would be something like:
 * Parameters params = new SimpleParameters();
 * params.load(ClassLoader.getSystemResource("test.params").openStream());
 * 
 * @author Jeff Hoye
 */
public class SimpleParameters extends Properties implements Parameters {

  // getters
  public int getInt(String paramName, int defaultVal) {
    String prop = getProperty(paramName); 
    if (prop == null) return defaultVal;
    return Integer.parseInt(prop);
  }
  
  public double getDouble(String paramName, double defaultVal) {
    String prop = getProperty(paramName); 
    if (prop == null) return defaultVal;
    return Double.parseDouble(prop);
  }

  public float getFloat(String paramName, float defaultVal) {
    String prop = getProperty(paramName); 
    if (prop == null) return defaultVal;
    return Float.parseFloat(prop);
  }

  /**
   * @return true if equalsIgnoreCase("true"), or an integer != 0; false if equalsIgnoreCase("false") or 0
   * @throws NumberFormatException if not true/false/integer
   */
  public boolean getBoolean(String paramName, boolean defaultVal) {
    String prop = getProperty(paramName); 
    if (prop == null) return defaultVal;
    
    if (prop.equalsIgnoreCase("true")) return true;
    if (prop.equalsIgnoreCase("false")) return false;

    
    int foo = Integer.parseInt(prop);
    if (foo == 0) return false;
    return true;
  }

  public InetAddress getInetAddress(String paramName, String defaultVal) throws UnknownHostException {
    String prop = getProperty(paramName); 
    if (prop == null) {
      if (defaultVal == null) return null;
      return InetAddress.getByName(defaultVal);
    }
    return InetAddress.getByName(prop);
  }

  public InetAddress getInetAddress(String paramName, InetAddress defaultVal) throws UnknownHostException {
    String prop = getProperty(paramName); 
    if (prop == null) {
      return defaultVal;
    }
    return InetAddress.getByName(prop);
  }
  
  public InetSocketAddress getInetSokcetAddress(String paramName, String defaultVal) {
    String prop = getProperty(paramName); 
    if (prop == null) {
      if (defaultVal == null) return null;
      return getInetSocketAddress(defaultVal);
    }
    return getInetSocketAddress(prop);
  }

  public InetSocketAddress getInetSokcetAddress(String paramName, InetSocketAddress defaultVal) {
    String prop = getProperty(paramName); 
    if (prop == null) {
      return defaultVal;
    }
    return getInetSocketAddress(prop);
  }

  public InetSocketAddress[] getInetSokcetAddresses(String paramName, String defaultVal) {
    String prop = getProperty(paramName); 
    if (prop == null) {
      if (defaultVal == null) return null;
      return getInetSocketAddresses(defaultVal);
    }
    return getInetSocketAddresses(prop);
  }
    
 

  public InetSocketAddress[] getInetSokcetAddresses(String paramName, InetSocketAddress[] defaultVal) {
    String prop = getProperty(paramName); 
    if (prop == null) {
      return defaultVal;
    }
    return getInetSocketAddresses(prop);
  }

  // setters
  public void setInt(String paramName, int val) {
    setProperty(paramName, String.valueOf(val));    
  }

  public void setDouble(String paramName, double val) {
    setProperty(paramName, String.valueOf(val));    
  }

  public void setFloat(String paramName, float val) {
    setProperty(paramName, String.valueOf(val));    
  }

  public void setBoolean(String paramName, boolean val) {
    setProperty(paramName, String.valueOf(val));    
  }

  public void setInetAddress(String paramName, InetAddress val) {
    setProperty(paramName, val.toString());    
  }

  public void setInetSokcetAddress(String paramName, InetSocketAddress val) {
    setProperty(paramName, val.toString());        
  }

  /**
   * Stores comma-separated list of val
   */
  public void setInetSokcetAddresses(String paramName, InetSocketAddress[] val) {
    String ret = "";
    if (val != null) {
      if (val.length > 0) {
        ret+=val[0].toString();
        for (int ctr = 1; ctr < val.length; ctr++) {
          ret+=","+val[ctr].toString();
        }
      }
    }
    setProperty(paramName, ret);
  }

  // helpers
  /**
   * Splits addr on the : and passes the parts to new InetSocketAddress()
   * @param addr
   * @return
   */
  private static InetSocketAddress getInetSocketAddress(String addr) {
    String[] parts = addr.split(":");
    return new InetSocketAddress(parts[0],Integer.parseInt(parts[1]));
  }

  /**
   * Splits addr on the , and passes the parts to getInetSocketAddress()
   * @param addrs
   * @return
   */
  private static InetSocketAddress[] getInetSocketAddresses(String addrs) {
    String[] addr = addrs.split(",");
    InetSocketAddress[] ret = new InetSocketAddress[addr.length];
    for (int ctr = 0; ctr < addr.length; ctr++) {
      ret[ctr] = getInetSocketAddress(addr[ctr]);
    }
    return ret;
  }

}
