/*
 * Created on May 4, 2005
 */
package rice.p2p.libra;


// The UDPLibraServer invokes these methods on the client, the ESMClient in turns sends UDP messages which cause the invokation of these methods  
public interface ESMClient {
    public void invokeRegister(int esmStreamId, byte[] esmOverlayId, int esmServerPort, int esmDatapathPort, byte esmRunId);
    public void invokeDummyRegister(byte[] dummyesmOverlayId, int dummyesmServerPort, int dummyesmDatapathPort, byte dummyesmRunId);
    public void invokeAnycast(int index, int seqNum, int pathLength, byte[] paramsPath);
    public void invokeGrpMetadataRequest(int index, int seqNum);
    
    public void invokeSubscribe(int index);
    public void invokeUnsubscribe(int index);
    public void invokeUpdate(int index, int[] paramsLoad, int[] paramsLoss, int time, int pathLength, byte[] paramsPath) ;
    public void recvUDPQuery(byte[] payload);
    public void recvUDPAck(byte[] payload);
    
}
