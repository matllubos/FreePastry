
package rice.proxy;

import java.io.*;
import java.net.*;
import java.util.*;

/**
* This class represents a generic Java process launching program which reads in
 * preferences from a preferences file and then invokes another JVM using those
 * prefs.  If the launched JVM dies, this process can be configured to restart
 * the JVM any number of times before giving up.  This process can also be configured
 * to launch the second JVM with a specified memory allocation, etc...
 *
 * @author Alan Mislove
 */
public class Parameters {
  
  private MyProperties properties;
  
  private MyProperties defaults;
		
	private String fileName;
	
	private static String FILENAME_EXTENSION = ".params";
  private static String ARRAY_SPACER = ",";
	
	public Parameters(String fileName) throws IOException {
		this.fileName = fileName + FILENAME_EXTENSION;
    this.properties = new MyProperties();
    this.defaults = new MyProperties();
    
    this.defaults.load(ClassLoader.getSystemResource("default" + FILENAME_EXTENSION).openStream());

    if (new File(this.fileName).exists()) 
      properties.load(new FileInputStream(this.fileName));
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
      System.err.println("WARNING: The parameter '" + name + "' was not found - this is likely going to cause an error.  You " +
                         "can fix this by adding this parameter (and appropriate value) to the proxy.params file in your ePOST " +
                         "directory.");
    
    return result;
  }
  
  protected void setProperty(String name, String value) {
    if (defaults.getProperty(name).equals(value)) {
      if (properties.getProperty(name) != null) {
        properties.remove(name);
        writeFile();
      }
    } else {
      if ((properties.getProperty(name) == null) || (! properties.getProperty(name).equals(value))) {
        properties.setProperty(name, value);
        writeFile();
      }
    }
  }
  
  public void removeParameter(String name) {
    properties.remove(name); 
  }
  
  public boolean containsParameter(String name) {
    return properties.containsKey(name);
  }
	
	public int getIntParameter(String name) {
		return Integer.parseInt(getProperty(name));
	}
	
	public double getDoubleParameter(String name) {
		return Double.parseDouble(getProperty(name));
	}
	
	public long getLongParameter(String name) {
		return Long.parseLong(getProperty(name));
	}
  
	public boolean getBooleanParameter(String name) {
		return (new Boolean(getProperty(name))).booleanValue();
	}
  
  public InetSocketAddress getInetSocketAddressParameter(String name) throws UnknownHostException {
    return parseInetSocketAddress(getStringParameter(name));
  }
  
  public InetSocketAddress[] getInetSocketAddressArrayParameter(String name) throws UnknownHostException {
    String[] addresses = getStringParameter(name).split(ARRAY_SPACER);
    List result = new LinkedList();

    for (int i=0; i<addresses.length; i++) {
      InetSocketAddress address = parseInetSocketAddress(addresses[i]);
      
      if (address != null)
        result.add(address);
    }
    
    return (InetSocketAddress[]) result.toArray(new InetSocketAddress[0]);
  }
  
	public String getStringParameter(String name) {
		return getProperty(name);
	}
  
	public String[] getStringArrayParameter(String name) {
		String list = getProperty(name);
    
    if (list != null)
      return (list.equals("") ? new String[0] : list.split(ARRAY_SPACER));
    else
      return null;
	}
  
	public void setIntParameter(String name, int value) {
		setProperty(name, Integer.toString(value));
	}
	
	public void setDoubleParameter(String name, double value) {
		setProperty(name, Double.toString(value));
	}
	
	public void setLongParameter(String name, long value) {
		setProperty(name, Long.toString(value));
	}
	
	public void setBooleanParameter(String name, boolean value) {
		setProperty(name, "" + value);
	}
  
  public void setInetSocketAddressParameter(String name, InetSocketAddress value) {
    setProperty(name, value.getAddress().getHostAddress() + ":" + value.getPort());
  }
  
  public void setInetSocketAddressArrayParameter(String name, InetSocketAddress[] value) {
    StringBuffer buffer = new StringBuffer();
    
    for (int i=0; i<value.length; i++) {
      buffer.append(value[i].getAddress().getHostAddress() + ":" + value[i].getPort());
      if (i < value.length-1) 
        buffer.append(ARRAY_SPACER);
    }
    
    setProperty(name, buffer.toString());
  }
	
	public void setStringParameter(String name, String value) {
		setProperty(name, value);
	}
  
  public void setStringArrayParameter(String name, String[] value) {
    StringBuffer buffer = new StringBuffer();
    
    for (int i=0; i<value.length; i++) {
      buffer.append(value[i]);
      if (i < value.length-1) 
        buffer.append(ARRAY_SPACER);
    }
    
    setProperty(name, buffer.toString());
  }
  
  public void writeFile() {
    try {
      properties.store(new FileOutputStream(fileName), null);
    } catch(IOException ioe) {
      System.out.println("[Loader       ]: Unable to store properties file " + fileName + ", got error " + ioe);
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
