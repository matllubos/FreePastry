package rice.email.proxy.dns;

import java.io.*;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import rice.environment.Environment;
import rice.environment.logging.Logger;

public class DnsServiceImpl implements DnsService {
  DirContext ictx;
  Environment environment;
  
  public DnsServiceImpl(Environment env) throws IOException {
    this.environment = env;
    try {
      ictx = new InitialDirContext();
    } catch (NamingException e) {
      throw new IOException("Got exception " + e + " while starting DNS server.");
    }
  }

  public String[] lookup(String host) {
    try {
      if (host == null)
        return new String[0];

      Attributes jndiAttrs = ictx.getAttributes("dns:///" + host,
                                                new String[] {"MX"});

      Attribute result = jndiAttrs.get("mx");

      int numRecords = result.size();

      int[] priorities = new int[numRecords];
      String[] hosts = new String[numRecords];

      for (int i = 0; i < numRecords; i++) {
        String rawRecord = (String) result.get(i);
        String[] record = rawRecord.split(" ");
        int priority = Integer.parseInt(record[0]);
        String hostName = record[1];

        priorities[i] = priority;
        hosts[i] = hostName;
      }

      sort(priorities, hosts);

      return hosts;
    } catch (NamingException e) {
      environment.getLogManager().getLogger(DnsServiceImpl.class, null).logException(Logger.WARNING, 
          "Error looking up MX record for " + host + " due to ", e);

      return new String[0];
    }
  }

  private void sort(int[] nums, String[] data) {

    // moving down the array
    for (int i = 0; i < nums.length; i++)
    {

      // find the smallest lef
      int minLoc = i;
      int min = nums[i];
      for (int j = i; j < nums.length; j++)
      {
        if (nums[j] < min)
        {
          minLoc = j;
          min = nums[j];
        }
      }

      // and move it to the current location
      swap(nums, data, i, minLoc);
    }
  }

  private void swap(int[] nums, String[] data, int i, int j) {
    int tmpShort = nums[i];
    String tmpString = data[i];
    nums[i] = nums[j];
    data[i] = data[j];
    nums[j] = tmpShort;
    data[j] = tmpString;
  }
}
