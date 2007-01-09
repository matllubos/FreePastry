/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
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
  Logger logger;
  
  public DnsServiceImpl(Environment env) throws IOException {
    this.environment = env;
    this.logger = env.getLogManager().getLogger(getClass(),null);
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
      if (logger.level <= Logger.WARNING) logger.logException(
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
