/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/
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