package rice.email.proxy.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;


public class FileResource
    implements Resource
{
    File _file;
    Set openReaders = new HashSet();
    Set openInputStreams = new HashSet();
    Set openWriters = new HashSet();

    public FileResource(File file)
    {
        _file = file;
    }

    public void delete()
                throws IOException
    {
        closeAll(openReaders);
        closeAll(openInputStreams);
        closeAll(openWriters);

        if (!_file.delete())
            throw new IOException("Unable to delete " + _file);
    }

    private void closeAll(Set set)
    {
        for (Iterator i = set.iterator(); i.hasNext();)
        {
            try
            {
                Object o = i.next();
                o.getClass().getMethod("close", new Class[] {}).invoke(
                        o, new Object[] {});
            }
            catch (Exception ignore)
            {
            }
        }
    }

    public InputStream getInputStream()
                               throws IOException
    {
        InputStream in = new BufferedInputStream(new FileInputStream(
                                                         _file));
        openInputStreams.add(in);

        return in;
    }

    public Reader getReader()
                     throws IOException
    {
        Reader in = new BufferedReader(new FileReader(_file));
        openReaders.add(in);

        return in;
    }

    public Writer getWriter()
                     throws IOException
    {
        Writer out = new BufferedWriter(new FileWriter(_file));
        openWriters.add(out);

        return out;
    }

    public long getSize()
    {

        return _file.length();
    }
}