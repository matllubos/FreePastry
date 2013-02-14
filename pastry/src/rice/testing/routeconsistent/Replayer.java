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
package rice.testing.routeconsistent;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rice.pastry.Id;


public class Replayer {
  public static void main(String[] args) throws Exception {
    String idString = "7B2C2EC47F036DADB5B998571D483EA8033F0FA4";
    String addrString = "planetlab01.mpi-sws.mpg.de/139.19.142.1:21854";
    String startTimeString = "1193145369648";
    String randSeedString = "-3326724064924085497";
    String bootAddrCString = "planetlab02.mpi-sws.mpg.de/139.19.142.2:21854";
    
//    String bootAddressString = ConsistencyPLTest.ALT_BOOTNODE;
//    int bootPort = ConsistencyPLTest.startPort;

    Id id = Id.build(idString);
    InetSocketAddress addr = getAddr(addrString).get(0);
    long startTime = Long.parseLong(startTimeString);
    long randSeed = Long.parseLong(randSeedString);
    
    
    List<InetSocketAddress> bootAddrCandidates = getAddr(bootAddrCString);

    System.out.println(bootAddrCandidates);
    
//    ConsistencyPLTest.replayNode(id, addr, bootAddrCandidates, startTime, randSeed);
  }
  
  public static List<InetSocketAddress> getAddr(String s) throws UnknownHostException {
    ArrayList<InetSocketAddress> ret = new ArrayList<InetSocketAddress>();    
    Pattern inetSAddrPattern = Pattern.compile("/([0-9.]+):([0-9]+)");
    Matcher matcher = inetSAddrPattern.matcher(s);    
    while(matcher.find()) {
      ret.add(new InetSocketAddress(InetAddress.getByName(matcher.group(1)), Integer.parseInt(matcher.group(2))));
    }
    return ret;
  }
}
