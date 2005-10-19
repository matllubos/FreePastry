README for Common API Applications 
----------------------------------

FreePastry 1.3
06/10/2003

http://www.cs.rice.edu/CS/Projects/Systems/FreePastry

----------------------------------

OVERVIEW

The rice.p2p.commonapi package provides an interface similar to the one provided
in 'Towards a Common API for Structured Peer-to-Peer Overlays', by F. Dabek, 
B. Zhao, P. Druschel, J. Kubiatowicz, and I. Stoica, published in the second
International Workshop on Peer-to-Peer Systems, Berkeley, CA, February 2003.  The
API is designed to allow applications to be written in a protocol-independent manner,
allowing the easy migration of applications from Pastry to Chord to CAN to Tapestry, 
etc....  Applications need only interact with the interfaces in the rice.p2p.commonapi
package, and need not worry about anything in the rice.pastry packages.

Below, each of the interfaces is briefly described, and sample code showing how to
initialize and starting using the Pastry-driven common API.

-----------------------------------

INTERFACE DESCRIPTIONS

Application.java
  This is an interface which all applications on top of a Node must export.  This 
  interface allows the underlying node to deliver message, inform the application
  of passing messages, and changes in the neighbor nodes.
  
Endpoint.java
  This interface represents an endpoint which applications can use to send messages
  from.  An endpoint is obtained by the registerApplication() method in Node.  The
  endpoint represents the applications' view of the world.
  
Id.java
  This interface is the abstraction of an Id in a structured overlay.  The only
  assumption that is made is that the Id space is circular.  An Id could represent
  a live node in the network, an object in the network, or simply a random Id.
  
IdFactory.java
  This interface represents a factory for Ids, which allows applications to create
  protocol-specific Ids from byte arrays, int arrays, and other Ids.  The factory 
  also exports methods which allow the creation of protocol-specific IdRange and 
  IdSet implementations.
  
IdRange.java
  This interface represents a range of Ids which is a subset of the whole Id space.
  The subset must be a single range, and hence has a left bound and a right bound.
  
IdSet.java
  This interface represents a set of distinct Ids from the Id space.  Applications
  can get an iterator from the set, as well as add and remove items to/from the set.
  
Message.java
  This interface represents the abstraction of a message in the common API.  Thus, 
  and messages sent to other nodes should extend or implement this class.
  
Node.java
  This interface is the abstraction of the local node in the common API.  Thus, the
  node provides a way of obtaining a protocol-specific IdFactory, as well as a way
  for an application to obtain it's own endpoint.
  
NodeHandle.java
  This interface represents a handle to a remote live node in the ring.  The node 
  handle differs from an Id in that it represents an actual live node, and contains
  the underlying network address for this node.  The handle can be used for sending
  messages directly to the remote node, instead of routing through the overlay.
  
NodeHandleSet.java
  This interface represents a set of NodeHandles, similar to the IdSet interface
  
RouteMessage.java
  This interface represents a message which is currently wrapped inside of a protocol-
  specific routing message.  This allows applications to change the wrapped message, as
  well as changing the routing characteristics of the underlying protocol.
  
  
------------------------------------

GETTING STARTED

In order to bootstrap an application, you will need to provide it with a live Node.  Thus, 
you will need to use a few Pastry classes in order to create and boot the network.  Note, 
however, that once the nodes are created, applications should not need to know anything
about Pastry. In Pastry, this is obtained by using one of the following factories:

  rice.pastry.direct.DirectPastryNodeFactory   // for simulated nodes
  rice.pastry.dist.DistPastryNodeFactory       // for distributed nodes using RMI or wire
  
In the case of the direct protocol, a new node is obtained by using

  DirectPastryNodeFactory.newNode(NodeHandle handle)
  
in the case of the first node in the network, the handle should be null.  Subsequent nodes
can be created by passing in the handle from the Endpoint.getLocalNodeHandle() method.
  
In the cases of the distributed protocols, a new node is obtained by using 

  DistPastryNodeFactory.newNode(InetSocketAddress address)
  
where the address is the address (IP + port) of the boot node.  Again, in the case of the
first node in the network, one should pass in null.  You can select the protocol by using
the static method
  
  DistPastryNodeFactory.getFactory(new IPNodeIdFactory(), PROTOCOL, port)
  
where PROTOCOL is one of 

  DistPastryNodeFactory.PROTOCOL_RMI
  DistPastryNodeFactory.PROTOCOL_WIRE
  
Note that the wire protocol is still in beta, as the underlying Java NIO architecture is
unreliable on some platforms.  We recommend using the RMI protocol if the wire protocol
causes problems.


-----------------------------------

SAMPLE CODE

A sample of how to get a network up is shown below.  In addition, you can look at the 
CommonAPITest and PastRegrTest for examples of building networks.  We assume that
the variables BOOTSTRAP_HOST, BOOTSTRAP_PORT, PROTOCOL and PORT have been set apropriately.

  IPNodeIdFactory idFactory = new IPNodeIdFactory(PORT);
  DistPastryNodeFactory factory = DistPastryNodeFactory.getFactory(idFactory,
                                                                   PROTOCOL,
                                                                   PORT);
  InetSocketAddress address = new InetSocketAddress(BOOTSTRAP_HOST, BOOTSTRAP_PORT);
  Node node1 = factory.newNode(factory.getNodeHandle(address));
  Node node2 = factory.newNode(factory.getNodeHandle(address));
  ...
  Past past1 = new PastImpl(node2, storage2, REPLICATION_FACTOR, "Test");
  Past past1 = new PastImpl(node2, storage2, REPLICATION_FACTOR, "Test");