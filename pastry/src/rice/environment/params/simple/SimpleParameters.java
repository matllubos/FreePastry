
package rice.environment.params.simple;

import java.io.*;
import java.net.*;
import java.util.*;

import rice.environment.params.Parameters;

/**
* This class represents a generic Java process launching program which reads in
 * preferences from a preferences file and then invokes another JVM using those
 * prefs.  If the launched JVM dies, this process can be configured to restart
 * the JVM any number of times before giving up.  This process can also be configured
 * to launch the second JVM with a specified memory allocation, etc...
 *
 * @author Alan Mislove
 */
public class SimpleParameters implements Parameters {
  
  private MyProperties properties;
  
  private MyProperties defaults;
		
  /**
   * Where items are written out.
   */
	private String configFileName;
	
	private static String FILENAME_EXTENSION = ".params";
  private static String ARRAY_SPACER = ",";
	
	public SimpleParameters(String[] orderedDefaults, String configFileName) throws IOException {
    if (configFileName != null) {
  		this.configFileName = configFileName + FILENAME_EXTENSION;
    }
    this.properties = new MyProperties();
    this.defaults = new MyProperties();

    for (int ctr = 0; ctr < orderedDefaults.length; ctr++) {
      try {
        this.defaults.load(ClassLoader.getSystemResource(orderedDefaults[ctr] + FILENAME_EXTENSION).openStream());
      } catch (Exception ioe) {
        System.err.println("Warning, couldn't load param file:"+(orderedDefaults[ctr]+FILENAME_EXTENSION));
        ioe.printStackTrace(System.err);        
//        throw new DefaultParamsNotPresentException(ioe); 
      }
    }

    if (this.configFileName != null) {
      if (new File(this.configFileName).exists()) { 
        properties.load(new FileInputStream(this.configFileName));
      }
    }
	}
  
  public Enumeration enumerateDefaults() {
    return defaults.keys(); 
  }
  
  public Enumeration enumerateNonDefaults() {
    return properties.keys(); 
  }
  
  protected InetSocketAddress parseInetSocketAddress(String name) throws UnknownHostException {
    String host = name.substring(0, name.indexOf(":"));
    String port = name.substring(name.indexOf(":")+1);
    
    try {
      return new InetSocketAddress(InetAddress.getByName(host), Integer.parseInt(port));
    } catch (UnknownHostException uhe) {
      System.err.println("ERROR: Unable to find IP for ISA " + name + " - returning null.");
      return null;
    }
  }
  
  protected String getProperty(String name) {
    String result = properties.getProperty(name);
    
    if (result == null) 
      result = defaults.getProperty(name);
    
    if (result == null) 
      System.err.println("WARNING: The parameter '" + name + "' was not found - this is likely going to cause an error.");
    //You " +
    //                     "can fix this by adding this parameter (and appropriate value) to the proxy.params file in your ePOST " +
    //                     "directory.");
    
    return result;
  }
  
  protected void setProperty(String name, String value) {
    if ((defaults.getProperty(name) != null) && (defaults.getProperty(name).equals(value))) {
      if (properties.getProperty(name) != null) {
        properties.remove(name);
        store();
      }
    } else {
      if ((properties.getProperty(name) == null) || (! properties.getProperty(name).equals(value))) {
        properties.setProperty(name, value);
        store();
      }
    }
  }
  
  public void remove(String name) {
    properties.remove(name); 
  }
  
  public boolean contains(String name) {
    return properties.containsKey(name);
  }
	
	public int getInt(String name) {
		return Integer.parseInt(getProperty(name));
	}
	
  public double getDouble(String name) {
    return Double.parseDouble(getProperty(name));
  }
  
  public float getFloat(String name) {
    return Float.parseFloat(getProperty(name));
  }
  
	public long getLong(String name) {
		return Long.parseLong(getProperty(name));
	}
  
	public boolean getBoolean(String name) {
		return (new Boolean(getProperty(name))).booleanValue();
	}
  
  public InetAddress getInetAddress(String name) throws UnknownHostException {
    return InetAddress.getByName(getString(name));
  
  }
  
  public InetSocketAddress getInetSocketAddress(String name) throws UnknownHostException {
    return parseInetSocketAddress(getString(name));
  }
  
  public InetSocketAddress[] getInetSocketAddressArray(String name) throws UnknownHostException {
    String[] addresses = getString(name).split(ARRAY_SPACER);
    List result = new LinkedList();

    for (int i=0; i<addresses.length; i++) {
      InetSocketAddress address = parseInetSocketAddress(addresses[i]);
      
      if (address != null)
        result.add(address);
    }
    
    return (InetSocketAddress[]) result.toArray(new InetSocketAddress[0]);
  }
  
	public String getString(String name) {
		return getProperty(name);
	}
  
	public String[] getStringArray(String name) {
		String list = getProperty(name);
    
    if (list != null)
      return (list.equals("") ? new String[0] : list.split(ARRAY_SPACER));
    else
      return null;
	}
  
	public void setInt(String name, int value) {
		setProperty(name, Integer.toString(value));
	}
	
  public void setDouble(String name, double value) {
    setProperty(name, Double.toString(value));
  }
  
  public void setFloat(String name, float value) {
    setProperty(name, Float.toString(value));
  }
  
	public void setLong(String name, long value) {
		setProperty(name, Long.toString(value));
	}
	
	public void setBoolean(String name, boolean value) {
		setProperty(name, "" + value);
	}
  
  public void setInetAddress(String name, InetAddress value) {
    setProperty(name, value.getHostAddress());
  }
  
  public void setInetSocketAddress(String name, InetSocketAddress value) {
    setProperty(name, value.getAddress().getHostAddress() + ":" + value.getPort());
  }
  
  public void setInetSocketAddressArray(String name, InetSocketAddress[] value) {
    StringBuffer buffer = new StringBuffer();
    
    for (int i=0; i<value.length; i++) {
      buffer.append(value[i].getAddress().getHostAddress() + ":" + value[i].getPort());
      if (i < value.length-1) 
        buffer.append(ARRAY_SPACER);
    }
    
    setProperty(name, buffer.toString());
  }
	
	public void setString(String name, String value) {
		setProperty(name, value);
	}
  
  public void setStringArray(String name, String[] value) {
    StringBuffer buffer = new StringBuffer();
    
    for (int i=0; i<value.length; i++) {
      buffer.append(value[i]);
      if (i < value.length-1) 
        buffer.append(ARRAY_SPACER);
    }
    
    setProperty(name, buffer.toString());
  }
  
  public void store() {
    if (configFileName == null) return;
    try {
      properties.store(new FileOutputStream(configFileName), null);
    } catch(IOException ioe) {
      System.err.println("[Loader       ]: Unable to store properties file " + configFileName + ", got error " + ioe);
    }
	}
  
  protected class MyProperties extends Properties {
    public Enumeration keys() {
      final String[] keys = (String[]) keySet().toArray(new String[0]);
      Arrays.sort(keys);
      
      return new Enumeration() {
        int pos = 0;
        public boolean hasMoreElements() {
          return (pos < keys.length);
        }
        public Object nextElement() {
          return keys[pos++];
        }
      };
    }
  }
}
