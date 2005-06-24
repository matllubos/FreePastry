package rice.p2p.util.testing;

import rice.environment.random.RandomSource;
import rice.environment.random.simple.SimpleRandomSource;
import rice.p2p.util.*;
import java.io.*;
import java.math.*;

import java.security.*;
import java.security.cert.*;
import java.security.spec.*;
import java.util.*;
import java.util.zip.*;
import javax.crypto.*;
import javax.crypto.spec.*;

public class EncryptedStreamUnit {

  public static void main(String[] argv) throws NoSuchAlgorithmException,
      IOException, ClassNotFoundException {
    
    int BUFFER_SIZE = 32678;
    System.out.println("EncryptedStream Test Suite");
    System.out
        .println("-------------------------------------------------------------");
    System.out.println("  Initializing Tests");
    System.out.print("    Generating key pairs\t\t\t\t");

    KeyPair pair = SecurityUtils.generateKeyAsymmetric();
    KeyPair pair2 = SecurityUtils.generateKeyAsymmetric();
    System.out.println("[ DONE ]");

    System.out.print("    Generating random number generator\t\t\t");
    RandomSource r = new SimpleRandomSource();
    System.out.println("[ DONE ]");

    System.out
        .println("-------------------------------------------------------------");
    System.out.println("  Running Tests");

    System.out.print("    Testing Simple Encryption\t\t\t\t");

    byte[] bytes = new byte[] { (byte) 0xa7, (byte) 0xb3, (byte) 0x00,
        (byte) 0x12, (byte) 0x4e };
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    EncryptedOutputStream eos = new EncryptedOutputStream(pair.getPublic(),
        baos, BUFFER_SIZE);

    eos.write(bytes);
    eos.close();

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    DataInputStream dis = new DataInputStream(new EncryptedInputStream(pair
        .getPrivate(), bais));

    byte[] read = new byte[bytes.length];
    dis.readFully(read);

    if (Arrays.equals(read, bytes)) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + MathUtils.toHex(bytes));
      System.out.println("    Output:\t" + MathUtils.toHex(read));
    }

    System.out.print("    Testing Multiple Encryption\t\t\t\t");

    baos = new ByteArrayOutputStream();
    eos = new EncryptedOutputStream(pair.getPublic(), baos, BUFFER_SIZE);

    eos.write(bytes);
    eos.write(bytes);
    eos.close();

    bais = new ByteArrayInputStream(baos.toByteArray());
    dis = new DataInputStream(new EncryptedInputStream(pair.getPrivate(), bais));

    read = new byte[bytes.length];
    dis.readFully(read);
    byte[] read2 = new byte[bytes.length];
    dis.readFully(read2);

    if (Arrays.equals(read, bytes) && Arrays.equals(read2, bytes)) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + MathUtils.toHex(bytes)
          + MathUtils.toHex(bytes));
      System.out.println("    Output:\t" + MathUtils.toHex(read)
          + MathUtils.toHex(read2));
    }

    System.out.print("    Testing Long Encryption\t\t\t\t");

    baos = new ByteArrayOutputStream();
    eos = new EncryptedOutputStream(pair.getPublic(), baos, BUFFER_SIZE);

    bytes = new byte[128000];
    r.nextBytes(bytes);

    eos.write(bytes);
    eos.close();

    bais = new ByteArrayInputStream(baos.toByteArray());
    dis = new DataInputStream(new EncryptedInputStream(pair.getPrivate(), bais));

    read = new byte[bytes.length];
    dis.readFully(read);

    if (Arrays.equals(read, bytes)) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + MathUtils.toHex(bytes));
      System.out.println("    Output:\t" + MathUtils.toHex(read));
    }

    System.out.print("    Testing Incorrect Decryption\t\t\t");

    baos = new ByteArrayOutputStream();
    eos = new EncryptedOutputStream(pair.getPublic(), baos, BUFFER_SIZE);

    bytes = new byte[128000];
    r.nextBytes(bytes);

    eos.write(bytes);
    eos.close();

    try {
      bais = new ByteArrayInputStream(baos.toByteArray());
      dis = new DataInputStream(new EncryptedInputStream(pair2.getPrivate(),
          bais));

      read = new byte[bytes.length];
      dis.readFully(read);

      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + MathUtils.toHex(bytes));
      System.out.println("    Output:\t" + MathUtils.toHex(read));
    } catch (SecurityException e) {
      System.out.println("[ PASSED ]");
    }

    System.out.print("    Testing Slow Decryption\t\t\t\t");

    baos = new ByteArrayOutputStream();
    eos = new EncryptedOutputStream(pair.getPublic(), baos, BUFFER_SIZE);

    bytes = new byte[128000];
    r.nextBytes(bytes);

    eos.write(bytes);
    eos.close();

    bais = new ByteArrayInputStream(baos.toByteArray());
    dis = new DataInputStream(new EncryptedInputStream(pair.getPrivate(), bais));

    read = new byte[1000];
    int c = 0;

    while (c < bytes.length) {
      dis.readFully(read);

      byte[] tmp = new byte[read.length];
      System.arraycopy(bytes, c, tmp, 0, read.length);

      if (!Arrays.equals(read, tmp)) {
        System.out.println("[ FAILED ]");
        System.out.println("    Iteration: \t" + c);
        System.out.println("    Input: \t" + MathUtils.toHex(tmp));
        System.out.println("    Output:\t" + MathUtils.toHex(read));
        break;
      }

      c += read.length;
    }

    if (c >= bytes.length) {
      System.out.println("[ PASSED ]");
    }

    System.out.print("    Testing Randon Encryption\t\t\t\t");

    baos = new ByteArrayOutputStream();
    eos = new EncryptedOutputStream(pair.getPublic(), baos, BUFFER_SIZE);
    byte[][] data = new byte[1000][];

    for (int i = 0; i < data.length; i++) {
      data[i] = new byte[r.nextInt(594)];
      r.nextBytes(data[i]);
      eos.write(data[i]);
    }

    eos.close();

    bais = new ByteArrayInputStream(baos.toByteArray());
    dis = new DataInputStream(new EncryptedInputStream(pair.getPrivate(), bais));
    int j = 0;

    for (j = 0; j < data.length; j++) {
      read = new byte[data[j].length];
      dis.readFully(read);

      if (!Arrays.equals(read, data[j])) {
        System.out.println("[ FAILED ]");
        System.out.println("    Iteration: \t" + c);
        System.out.println("    Input: \t" + MathUtils.toHex(data[j]));
        System.out.println("    Output:\t" + MathUtils.toHex(read));
        break;
      }
    }

    if (j == data.length) {
      System.out.println("[ PASSED ]");
    }

    System.out
        .println("-------------------------------------------------------------");
  }
}