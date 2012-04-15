package rice.pastry.secureconnection.test;

import java.net.InetSocketAddress;
import java.util.Timer;
import java.util.TimerTask;
import rice.p2p.commonapi.Message;
import rice.p2p.scribe.ScribeContent;

/**
 *
 * @author Luboš Mátl
 */
public class ScribeTest implements MessageReceiverI {

    static Timer timer = new Timer();

    @Override
    public void receive(ScribeContent content) {
        System.out.println("\tok");
        timer.cancel();
        timer = new Timer();
    }

    @Override
    public void receive(Message content) {
        System.out.println("\tok");
        timer.cancel();
        timer = new Timer();
    }

    public static void main(String args[]) throws Exception {

        Connector connector = new Connector();
        MessagingApp receiver = connector.getSender(new InetSocketAddress("127.0.0.1", 6000), new InetSocketAddress("127.0.0.1", 6000), new ScribeTest());
        Thread.sleep(1000);
        MessagingApp sender = connector.getSender(new InetSocketAddress("127.0.0.1", 6001), new InetSocketAddress("127.0.0.1", 6000), new ScribeTest());
        Thread.sleep(1000);
        receiver.join("scribe-test");
        Thread.sleep(1000);
        
        System.out.println("------------------------");
        
        for (int i = 0; i < 10; i++) {
            System.out.println("Scribe message "+i);
            sender.send(new TestScribeMessage(), "scribe-test");
            timer.schedule(new ErrorTask(), 1000);
            Thread.sleep(1500);
        }
        
        System.out.println("------------------------");
        
        for (int i = 0; i < 10; i++) {
            System.out.println("Pastry message "+i);
            sender.send(new TestMessage(), receiver.getLocalId());
            timer.schedule(new ErrorTask(), 1000);
            Thread.sleep(3000);
        }
        System.exit(0);
    }

    private static class ErrorTask extends TimerTask {

        @Override
        public void run() {
            System.out.println("\terror");
        }
    }

    private static class TestScribeMessage implements ScribeContent {
    }

    private static class TestMessage implements Message {

        @Override
        public int getPriority() {
            return 1;
        }
    }
}
