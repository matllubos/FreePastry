package rice.email.proxy.util;

import java.io.IOException;
import java.io.OutputStream;


public class SpyOutputStream
    extends OutputStream
{
    OutputStream b;
    OutputStream a;
    boolean debug = true;

    public SpyOutputStream(OutputStream a, OutputStream b)
    {
        this.a = a;
        this.b = b;
    }

    public void setDebug(boolean value)
    {
        debug = value;
    }

    public void write(int value)
               throws IOException
    {
        write(new byte[] {(byte) value});
    }

    public void write(byte[] buf, int start, int len)
               throws IOException
    {
        a.write(buf, start, len);
        if (debug)
        {
            b.write(buf, start, len);
            b.flush();
        }
    }

    public void write(byte[] buf)
               throws IOException
    {
        write(buf, 0, buf.length);
    }

    public void close()
               throws IOException
    {
        a.close();
        b.close();
    }

    public void flush()
               throws IOException
    {
        a.flush();
        b.flush();
    }
}