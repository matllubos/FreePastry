package rice.splitstream.testing;
public class SplitStreamUnitTest{

   private BandwidthUnitTest bandwidthTest;
   private StripeUnitTest stripeTest;
   private ChannelUnitTest channelTest;

   public static void main(String argv[]){
     System.out.println("Starting SplitStream System Unit Tests");
     SplitStreamUnitTest test = new SplitStreamUnitTest();
     test.init();
     test.run();
   }

   public void init(){
    /* Set up all the tests */
    bandwidthTest = new BandwidthUnitTest();
    stripeTest = new StripeUnitTest();
    channelTest = new ChannelUnitTest();

   }
 
   public boolean run(){
    /* run all set up tests */
    bandwidthTest.run();
    stripeTest.run();
    channelTest.run();
    return true;
   }
}
