/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/

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
  
  private MyProperties properties = new MyProperties();
		
	private String fileName;
	
	private static String FILENAME_EXTENSION = ".params";
  private static String ARRAY_SPACER = ",";
	
	public Parameters(String fileName) throws IOException {
		this.fileName = fileName + FILENAME_EXTENSION;
    
    if (new File(this.fileName).exists()) {
      properties.load(new FileInputStream(this.fileName));
    } else {
      properties.load(ClassLoader.getSystemResource(this.fileName).openStream());
      writeFile();
    }
	}
  
  protected InetSocketAddress parseInetSocketAddress(String name) throws UnknownHostException {
    String host = name.substring(0, name.indexOf(":"));
    String port = name.substring(name.indexOf(":")+1);
    return new InetSocketAddress(InetAddress.getByName(host), Integer.parseInt(port));
  }
  
  public void removeParameter(String name) {
    properties.remove(name); 
  }
	
	public int getIntParameter(String name) {
		return Integer.parseInt(properties.getProperty(name));
	}
	
	public double getDoubleParameter(String name) {
		return Double.parseDouble(properties.getProperty(name));
	}
	
	public long getLongParameter(String name) {
		return Long.parseLong(properties.getProperty(name));
	}
  
	public boolean getBooleanParameter(String name) {
		return (new Boolean(properties.getProperty(name))).booleanValue();
	}
  
  public InetSocketAddress getInetSocketAddressParameter(String name) throws UnknownHostException {
    return parseInetSocketAddress(getStringParameter(name));
  }
  
  public InetSocketAddress[] getInetSocketAddressArrayParameter(String name) throws UnknownHostException {
    String[] addresses = getStringParameter(name).split(ARRAY_SPACER);
    InetSocketAddress[] result = new InetSocketAddress[addresses.length];
      
    for (int i=0; i<result.length; i++)
      result[i] = parseInetSocketAddress(addresses[i]);
    
    return result;
  }
  
	public String getStringParameter(String name) {
		return properties.getProperty(name);
	}
  
	public String[] getStringArrayParameter(String name) {
		String list = properties.getProperty(name);
    
    if (list != null)
      return list.split(ARRAY_SPACER);
    else
      return null;
	}
  
	public void setIntParameter(String name, int value) {
		properties.setProperty(name, Integer.toString(value));
	}
	
	public void setDoubleParameter(String name, double value) {
		properties.setProperty(name, Double.toString(value));
	}
	
	public void setLongParameter(String name, long value) {
		properties.setProperty(name, Long.toString(value));
	}
	
	public void setBooleanParameter(String name, boolean value) {
		properties.setProperty(name, "" + value);
	}
  
  public void setInetSocketAddressParameter(String name, InetSocketAddress value) {
    properties.setProperty(name, value.getAddress().getHostAddress() + ":" + value.getPort());
  }
  
  public void setInetSocketAddressArrayParameter(String name, InetSocketAddress[] value) {
    StringBuffer buffer = new StringBuffer();
    
    for (int i=0; i<value.length; i++) {
      buffer.append(value[i].getAddress().getHostAddress() + ":" + value[i].getPort());
      if (i < value.length-1) 
        buffer.append(ARRAY_SPACER);
    }
    
    properties.setProperty(name, buffer.toString());
  }
	
	public void setStringParameter(String name, String value) {
		properties.setProperty(name, value);
	}
  
  public void setStringArrayParameter(String name, String[] value) {
    StringBuffer buffer = new StringBuffer();
    
    for (int i=0; i<value.length; i++) {
      buffer.append(value[i]);
      if (i < value.length-1) 
        buffer.append(ARRAY_SPACER);
    }
    
    properties.setProperty(name, buffer.toString());
  }
	
	public void registerIntParameter(String name, int defaultValue) {
		if (properties.getProperty(name) == null) {
			setIntParameter(name, defaultValue);
			writeFile();
		}
	}
	
	public void registerDoubleParameter(String name, double defaultValue) {
		if (properties.getProperty(name) == null) {
			setDoubleParameter(name, defaultValue);
			writeFile();
		}
	}
  
	public void registerLongParameter(String name, long defaultValue) {
		if (properties.getProperty(name) == null) {
			setLongParameter(name, defaultValue);
			writeFile();
		}
	}
  
	public void registerBooleanParameter(String name, boolean defaultValue) {
		if (properties.getProperty(name) == null) {
			setBooleanParameter(name, defaultValue);
			writeFile();
		}
	}
  
	public void registerInetSocketAddressParameter(String name, InetSocketAddress defaultValue) {
		if (properties.getProperty(name) == null) {
			setInetSocketAddressParameter(name, defaultValue);
			writeFile();
		}
	}
  
	public void registerInetSocketAddressArrayParameter(String name, InetSocketAddress[] defaultValue) {
		if (properties.getProperty(name) == null) {
			setInetSocketAddressArrayParameter(name, defaultValue);
			writeFile();
		}
	}
  
	public void registerStringParameter(String name, String defaultValue) {
		if (properties.getProperty(name) == null) {
			setStringParameter(name, defaultValue);
			writeFile();
		}
	}
  
	public void registerStringArrayParameter(String name, String[] defaultValue) {
		if (properties.getProperty(name) == null) {

			writeFile();
		}
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