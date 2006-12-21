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
package rice.visualization.proxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import rice.email.proxy.EmailProxy;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.environment.processing.simple.SimpleProcessor;
import rice.p2p.multiring.MultiringNode;
import rice.pastry.dist.DistNodeHandle;
import rice.pastry.dist.DistPastryNode;
import rice.p2p.glacier.v2.GlacierImpl;
import rice.p2p.past.*;
import rice.p2p.util.DebugCommandHandler;
import rice.p2p.aggregation.AggregationImpl;
import rice.persistence.*;
import rice.visualization.*;
import rice.visualization.Visualization;
import rice.visualization.server.*;

public class VisualizationEmailProxy extends EmailProxy {
    
  protected VisualizationServer server;
  protected VisualizationServer globalServer;
  
  protected InetSocketAddress serverAddress;
  protected InetSocketAddress globalServerAddress;
  
  
  public void start2() throws Exception { 
    super.start2();
    MessageNamingService mns = new MessageNamingService();
    Parameters parameters = environment.getParameters();
    if (parameters.getBoolean("visualization_enable")) {
      DistPastryNode pastry = (DistPastryNode) ((MultiringNode) node).getNode();
      int visualizationPort = ((DistNodeHandle) pastry.getLocalHandle()).getAddress().getPort() + Visualization.PORT_OFFSET;
      
      sectionStart("Starting Visualization services");
      stepStart("Creating Visualization Server");
      try {
        this.serverAddress = new InetSocketAddress(getLocalHost(), 
                                                   visualizationPort);
      } catch (IOException e) {
        stepDone(FAILURE, e + "");
      }
      
      server = new VisualizationServer(serverAddress, pastry, immutableStorage, cert, new Object[] {pastry, immutablePast, immutableStorage}, environment);
      server.addPanelCreator(new OverviewPanelCreator(environment));
      NetworkActivityPanelCreator network = new NetworkActivityPanelCreator(environment);
      server.addPanelCreator(network);
      MessageDistributionPanelCreator message = new MessageDistributionPanelCreator(mns);
      server.addPanelCreator(message);
      RecentMessagesPanelCreator recent = new RecentMessagesPanelCreator(mns);
      server.addPanelCreator(recent);
      server.addPanelCreator(new PastryPanelCreator());
      server.addPanelCreator(new SourceRoutePanelCreator());
      server.addPanelCreator(new MultiPersistencePanelCreator(environment, new String[] {"Immutable", "Mutable", "Pending", "Delivered", "Glacier Immutable", "Aggregation Waiting"},
                                                              new StorageManagerImpl[] {immutableStorage, mutableStorage, pendingStorage, deliveredStorage, glacierImmutableStorage, aggrWaitingStorage}));
      server.addPanelCreator(new MultiPASTPanelCreator(timer, new String[] {"Immutable", "Mutable", "Pending", "Delivered"},
                                                       new PastImpl[] {(PastImpl) realImmutablePast, (PastImpl) mutablePast, pendingPast, deliveredPast}));
      server.addPanelCreator(new GCPanelCreator(timer, realImmutablePast));

      if (immutablePast instanceof AggregationImpl) {
        server.addPanelCreator(new AggregationPanelCreator(timer, (AggregationImpl) immutablePast)); 
        Past aggregateStore = ((AggregationImpl)immutablePast).getAggregateStore();
        if (aggregateStore instanceof GlacierImpl)
          server.addPanelCreator(new GlacierPanelCreator((GlacierImpl) aggregateStore));
      }
      
      server.addPanelCreator(new QueuePanelCreator(environment, ((SimpleProcessor)environment.getProcessor()).getQueue(), ((SimpleProcessor)environment.getProcessor()).getIOQueue()));
      if (smtp != null)
        server.addPanelCreator(new EmailPanelCreator(timer, smtp));
      
      if (immutablePast instanceof DebugCommandHandler)
        server.addDebugCommandHandler((DebugCommandHandler)immutablePast);
      if (mutablePast instanceof DebugCommandHandler)
        server.addDebugCommandHandler((DebugCommandHandler)mutablePast);
      
      pastry.addNetworkListener(network);
      pastry.addNetworkListener(recent);
      pastry.addNetworkListener(message);
      stepDone(SUCCESS);
      
      try {
        stepStart("Starting Visualization Server on port " + visualizationPort);
        Thread t = new Thread(server, "Visualization Server Thread");
        t.start();
        synchronized (server) {
          for (int i = 0; i < 10; i++) {
            if (server.running())
              break;
            server.wait(1000);
          }
          if (server.running()) {
            stepDone(SUCCESS);
          } else {
            stepDone(FAILURE);
          }
        }
        
        if (parameters.getBoolean("visualization_client_enable")) {
          stepStart("Launching Visualization Client");
          Ring[] r = new Ring[1];
          r[0] = new Ring("global", null, (DistNodeHandle) pastry.getLocalHandle());
          Visualization visualization = new Visualization(r, environment);
          stepDone(SUCCESS);
        }
      } catch (Exception e) {
        if (logger.level <= Logger.WARNING) logger.logException( "ERROR: Unable to launch Visualization server - continuing - " , e);
        stepDone(FAILURE);
      }
      
      
      if (globalNode != null) {
        DistPastryNode gpastry = (DistPastryNode) ((MultiringNode) globalNode).getNode();
        int globalVisualizationPort = ((DistNodeHandle) gpastry.getLocalHandle()).getAddress().getPort() + Visualization.PORT_OFFSET;

        stepStart("Creating Global Visualization Server");
        try {
          this.globalServerAddress = new InetSocketAddress(getLocalHost(), globalVisualizationPort);
        } catch (IOException e) {
          stepDone(FAILURE, e + "");
        }
        
        globalServer = new VisualizationServer(globalServerAddress, gpastry, null, globalCert, new Object[] {gpastry}, environment);
        globalServer.addPanelCreator(new OverviewPanelCreator(environment));
        NetworkActivityPanelCreator gnetwork = new NetworkActivityPanelCreator(environment);
        globalServer.addPanelCreator(gnetwork);
        MessageDistributionPanelCreator gmessage = new MessageDistributionPanelCreator(mns);
        globalServer.addPanelCreator(gmessage);
        RecentMessagesPanelCreator grecent = new RecentMessagesPanelCreator(mns);
        globalServer.addPanelCreator(grecent);
        globalServer.addPanelCreator(new PastryPanelCreator());
        globalServer.addPanelCreator(new SourceRoutePanelCreator());
        
        gpastry.addNetworkListener(gnetwork);
        gpastry.addNetworkListener(grecent);
        gpastry.addNetworkListener(gmessage);
        stepDone(SUCCESS);
        
        try {
          stepStart("Starting Global Visualization Server on port " + globalVisualizationPort);
          Thread t = new Thread(globalServer, "Global Visualization Server Thread");
          t.start();
          stepDone(SUCCESS);
        } catch (Exception e) {
          if (logger.level <= Logger.WARNING) logger.logException( "ERROR: Unable to launch Visualization server - continuing - " , e);
          stepDone(FAILURE);
        }
      }
    }
    
    dialogPrint("\n\nYour ePOST is now booted and ready.  You can connect your mail client\n" +
                "with the instructions shown on the http://www.epostmail.org website.\n");    
  }
  
  public static void main(String[] args) {
    VisualizationEmailProxy proxy = new VisualizationEmailProxy();
    proxy.start();
  }
  
}
