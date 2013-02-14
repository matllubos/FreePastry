package rice.pastry.secureconnection.test;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 *
 * @author Luboš Mátl
 */
public class TestConnectors {

    public static void main(String args[]) throws InterruptedException {

        SecureIdConnector connector = new SecureIdConnector();
        try {
            connector.getSender(new InetSocketAddress("127.0.0.1", 6000), new InetSocketAddress("127.0.0.1", 6000), null);
            Thread.sleep(1000);
            connector.getSender(new InetSocketAddress("127.0.0.1", 6001), new InetSocketAddress("127.0.0.1", 6000), null);
            Thread.sleep(1000);
            connector.getSender(new InetSocketAddress("127.0.0.1", 6002), new InetSocketAddress("127.0.0.1", 6001), null);
            Thread.sleep(1000);
            connector.getSender(new InetSocketAddress("127.0.0.1", 6003), new InetSocketAddress("127.0.0.1", 6002), null);
            Thread.sleep(1000);
            connector.getSender(new InetSocketAddress("127.0.0.1", 6004), new InetSocketAddress("127.0.0.1", 6003), null);
            Thread.sleep(1000);
            connector.getSender(new InetSocketAddress("127.0.0.1", 6005), new InetSocketAddress("127.0.0.1", 6004), null);
            Thread.sleep(5000);
            System.out.println("ok");
        } catch (IOException ex) {
            System.out.println("error");
        }

        System.out.println("\n-----------------------\n");
        
        connector = new CollisionSecureIdConnector();
        try {
            connector.getSender(new InetSocketAddress("127.0.0.1", 6006), new InetSocketAddress("127.0.0.1", 6000), null);
            Thread.sleep(1000);
            connector.getSender(new InetSocketAddress("127.0.0.1", 6007), new InetSocketAddress("127.0.0.1", 6000), null);
            Thread.sleep(1000);
            connector.getSender(new InetSocketAddress("127.0.0.1", 6008), new InetSocketAddress("127.0.0.1", 6001), null);
            Thread.sleep(1000);
            connector.getSender(new InetSocketAddress("127.0.0.1", 6009), new InetSocketAddress("127.0.0.1", 6002), null);
            Thread.sleep(1000);
            connector.getSender(new InetSocketAddress("127.0.0.1", 6010), new InetSocketAddress("127.0.0.1", 6003), null);
            Thread.sleep(1000);
            connector.getSender(new InetSocketAddress("127.0.0.1", 6011), new InetSocketAddress("127.0.0.1", 6004), null);
            Thread.sleep(5000);
            System.out.println("error");
        } catch (IOException ex) {
            Thread.sleep(1000);
            System.out.println("ok");
        }
        
        System.out.println("\n-----------------------\n");
        
        connector = new SecureIdIPNodeIdFactoryConnector();
        try {
            connector.getSender(new InetSocketAddress("127.0.0.1", 6012), new InetSocketAddress("127.0.0.1", 6000), null);
            Thread.sleep(1000);
            connector.getSender(new InetSocketAddress("127.0.0.1", 6013), new InetSocketAddress("127.0.0.1", 6000), null);
            Thread.sleep(1000);
            connector.getSender(new InetSocketAddress("127.0.0.1", 6014), new InetSocketAddress("127.0.0.1", 6001), null);
            Thread.sleep(1000);
            connector.getSender(new InetSocketAddress("127.0.0.1", 6015), new InetSocketAddress("127.0.0.1", 6002), null);
            Thread.sleep(1000);
            connector.getSender(new InetSocketAddress("127.0.0.1", 6016), new InetSocketAddress("127.0.0.1", 6003), null);
            Thread.sleep(1000);
            connector.getSender(new InetSocketAddress("127.0.0.1", 6017), new InetSocketAddress("127.0.0.1", 6004), null);
            Thread.sleep(5000);
            System.out.println("ok");
        } catch (IOException ex) {
            Thread.sleep(1000);
            System.out.println("error");
        }

        System.exit(0);
    }
}
