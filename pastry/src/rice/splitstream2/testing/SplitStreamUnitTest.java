package rice.splitstream.testing;
public class SplitStreamUnitTest{

   private BandwidthUnitTest bandwidthTest;
   private StripeUnitTest stripeTest;
   private ChannelUnitTest channelTest;

   public static void main(String argv[]){
     System.out.println("Starting SplitStream System Unit Tests");
     SplitStreamUnitTest test = new SplitStreamUnitTest();
     test.init();
     if(test.run()){
          System.out.println("All Unit Tests              [ PASSED ] " );
     }
     else{
          System.out.println("All Unit Tests              [ FAILED ] " );
     }
   }

   public void init(){
    /* Set up all the tests */
    bandwidthTest = new BandwidthUnitTest();
    stripeTest = new StripeUnitTest();
    channelTest = new ChannelUnitTest();

   }
 
   public boolean run(){
    boolean passed = true;
    /* run all set up tests */
    passed &= bandwidthTest.run();
    passed &= stripeTest.run();
    passed &= channelTest.run();
    return passed;
   }
}
