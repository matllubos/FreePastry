package org.mpisws.p2p.transport.sourceroute;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.P2PSocketReceiver;

import rice.environment.logging.Logger;

public class Forwarder<Identifier> {
  SourceRoute sr;
  P2PSocket<Identifier> socka;
  P2PSocket<Identifier> sockb;
  Logger logger;
  
  private class HalfPipe implements P2PSocketReceiver {
    P2PSocket from;
    P2PSocket to;
    ByteBuffer buf;
    
    public HalfPipe(P2PSocket from, P2PSocket to) {
      this.from = from;
      this.to = to;    
      buf = ByteBuffer.allocate(1024);
      from.register(true, false, this);
    }
    
    public String toString() {
      return "HalfPipe "+from+"=>"+to;    
    }

    public void receiveException(P2PSocket socket, IOException e) {
      logger.logException(this+" "+socket, e);
    }

    public void receiveSelectResult(P2PSocket socket, boolean canRead, boolean canWrite) throws IOException {
      if (canRead) {
        if (socket != from) throw new IOException("Expected to read from "+from+" got "+socket);      
        long result = from.read(buf);
        if (result == -1) {
          if (logger.level <= Logger.FINE) logger.log(from+" has shut down input, shutting down output on "+to);
          to.shutdownOutput();
          return;
        }
        if (logger.level <= Logger.FINER) logger.log("Read "+result+" bytes from "+from);
        buf.flip(); 
        to.register(false, true, this);        
      } else {
        if (canWrite) {
          if (socket != to) throw new IOException("Expected to write to "+to+" got "+socket);      
          
          long result = to.write(buf);         
          if (result == -1) {
            if (logger.level <= Logger.FINE) logger.log(to+" has closed, closing "+from);
            from.close();            
          }
          if (logger.level <= Logger.FINER) logger.log("Wrote "+result+" bytes to "+to);
          
          
          if (buf.hasRemaining()) {
            // keep writing
            to.register(false, true, this);
          } else {
            // read again
            buf.clear();
            from.register(true, false, this);
          }
        } else {
          throw new IOException("Didn't select for either "+socket+","+canRead+","+canWrite); 
        }
      }
    }
  }

  public Forwarder(SourceRoute<Identifier> sr, P2PSocket<Identifier> socka, P2PSocket<Identifier> sockb, Logger logger) {
    this.sr = sr;
    this.socka = socka;
    this.sockb = sockb;
    this.logger = logger;
    
    new HalfPipe(socka,sockb);
    new HalfPipe(sockb,socka);
  }
  
}
