package rice.persistence;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.SortedMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.management.OperationsException;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

import rice.Continuation;
import rice.environment.logging.Logger;
import rice.environment.processing.WorkRequest;
import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.IdFactory;
import rice.p2p.commonapi.IdRange;
import rice.p2p.commonapi.IdSet;
import rice.p2p.util.ImmutableSortedMap;
import rice.p2p.util.RedBlackMap;
import rice.p2p.util.ReverseTreeMap;
import rice.p2p.util.XMLObjectInputStream;
import rice.p2p.util.XMLObjectOutputStream;

/*
 * TODO: track storage usage (getTotalSize) (maximum use quotas)
 * TOOD: flush()
 * TODO: serialization exceptions
 */

public class DatabaseStorage implements Storage {
  protected rice.environment.Environment env;
  protected Environment dbenv;
  protected Database db;
  protected Database metadb;
  protected IdFactory idf;
  // the in-memory map used to store the metadata
  protected ReverseTreeMap meta;
  private Logger logger;

  // convenience constructor for compatibility with PersistentStorage 
  public DatabaseStorage(IdFactory idf, String name, String rootDir, long size, boolean index, rice.environment.Environment env) throws IOException {
      this(idf,name,rootDir,env);
  }
  
  public DatabaseStorage(IdFactory idf, String name, String rootDir, long size, rice.environment.Environment env) throws IOException {
    this(idf,name,rootDir,env);
  }
  
  public DatabaseStorage (IdFactory factory, String rootDir, long size, rice.environment.Environment env) throws IOException {
    this(factory, "default", rootDir, size, env);
  }
  
  public DatabaseStorage(IdFactory idf, String name, String rootDir, rice.environment.Environment env) throws IOException {
    this.env = env;
    this.logger = env.getLogManager().getLogger(this.getClass(), name);
    this.idf = idf;
    EnvironmentConfig ec = new EnvironmentConfig();
    ec.setAllowCreate(true);
    ec.setTransactional(true);
    try {
      File dbdir = new File(rootDir, name);
      if (!dbdir.exists())
        dbdir.mkdirs();
      dbenv = new Environment(dbdir, ec);
      Transaction trans = dbenv.beginTransaction(null, null);
      DatabaseConfig dc = new DatabaseConfig();
      dc.setAllowCreate(true);
      dc.setTransactional(true);
      db = dbenv.openDatabase(trans, "data", dc);
      metadb = dbenv.openDatabase(trans, "metadata", dc);
      meta = new ReverseTreeMap();
      
      Cursor cur = metadb.openCursor(trans,null);
      OperationStatus status;
      do {
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry value = new DatabaseEntry();
        status = cur.getNext(key,value,null);
        if (key.getData() != null) {
          meta.put(db2id(key),dbDeserialize(value));
        }
      } while (status == OperationStatus.SUCCESS);
      
      cur.close();
      
      trans.commit();
    } catch (DatabaseException e) {
      if (logger.level <= Logger.WARNING) logger.logException("could not open database "+name+" in "+rootDir+": ",e);
      throw new IOException("could not open database "+name+" in "+rootDir+": "+e);
    }
  }

  public void store(final Id id, final Serializable metadata, final Serializable obj,
      Continuation c) {
    if (id == null || obj == null) {
      c.receiveResult(Boolean.FALSE);
      return;
    }
    
    env.getProcessor().processBlockingIO(new WorkRequest(c, env.getSelectorManager()) {
      public Object doWork() throws Exception {
        Transaction trans = dbenv.beginTransaction(null, null);
        OperationStatus result = db.put(trans, id2db(id), dbSerialize(obj));
        metadb.put(trans, id2db(id), dbSerialize(metadata));
        trans.commit();
        synchronized (meta) {
          meta.put(id, metadata);
        }
        return result == OperationStatus.SUCCESS; 
      }
    });
  }

  public void unstore(final Id id, Continuation c) {
    env.getProcessor().processBlockingIO(new WorkRequest(c, env.getSelectorManager()) {
      public Object doWork() throws Exception {
        Transaction trans = dbenv.beginTransaction(null, null);
        OperationStatus result = db.delete(trans, id2db(id));
        metadb.delete(trans, id2db(id));
        trans.commit();
        synchronized (meta) {
          meta.remove(id);
        }
        return result == OperationStatus.SUCCESS;
      }
    });
  }

  public boolean exists(Id id) {
    synchronized (meta) {
      return meta.containsKey(id);
    }
  }

  public void getObject(final Id id, Continuation c) {
    env.getProcessor().processBlockingIO(new WorkRequest(c, env.getSelectorManager()) {
      public Object doWork() throws Exception {
        Transaction trans = dbenv.beginTransaction(null, null);
        DatabaseEntry dbe = new DatabaseEntry();
        OperationStatus result = db.get(trans, id2db(id), dbe, null);
        trans.commit(); 
        if (result == OperationStatus.SUCCESS) {
          return dbDeserialize(dbe);
        } else {
          // remove entry from metadata db
          Transaction tr = dbenv.beginTransaction(null, null);
          metadb.delete(tr, id2db(id));
          tr.commit();
          synchronized (meta) {
            meta.remove(id);
          }
          return null;
        }
      }
    });
  }

  public Serializable getMetadata(Id id) {
    synchronized (meta) {
      return (Serializable) meta.get(id);
    }
  }

  public void setMetadata(final Id id, final Serializable metadata, Continuation c) {
    if (! exists(id)) {
      c.receiveResult(new Boolean(false));
      return;
    }
    
    env.getProcessor().processBlockingIO(new WorkRequest(c, env.getSelectorManager()) {
      public Object doWork() throws Exception {
        Transaction trans = dbenv.beginTransaction(null, null);
        OperationStatus result = metadb.put(trans, id2db(id), dbSerialize(metadata));
        trans.commit();
        synchronized (meta) {
          meta.put(id, metadata);
        }
        return result == OperationStatus.SUCCESS; 
      }
    });
  }

  public void rename(final Id oldId, final Id newId, Continuation c) {
    env.getProcessor().processBlockingIO(new WorkRequest(c, env.getSelectorManager()) {
      public Object doWork() throws Exception {
        Transaction trans = dbenv.beginTransaction(null, null);
        DatabaseEntry dbe = new DatabaseEntry();
        OperationStatus result = db.get(trans, id2db(oldId), dbe, null);
        if (result != OperationStatus.SUCCESS) {
          trans.abort();
          return Boolean.FALSE;
        }
        result = db.put(trans, id2db(newId), dbe);
        if (result != OperationStatus.SUCCESS) {
          trans.abort();
          return Boolean.FALSE;
        }
        dbe = new DatabaseEntry();
        result = metadb.get(trans, id2db(oldId), dbe, null);
        if (result != OperationStatus.SUCCESS) {
          trans.abort();
          return Boolean.FALSE;
        }
        result = metadb.put(trans, id2db(newId), dbe);
        if (result != OperationStatus.SUCCESS) {
          trans.abort();
          return Boolean.FALSE;
        }
        trans.commit(); 
        synchronized (meta) {
          meta.put(newId, meta.get(oldId));
          meta.remove(oldId); 
        }
        return Boolean.TRUE;
      }
    });
  }

  public IdSet scan(IdRange range) {
    if (range.isEmpty())
      return idf.buildIdSet();
    else if (range.getCCWId().equals(range.getCWId())) 
      return scan();
    else 
      synchronized (meta) {
        return idf.buildIdSet(new ImmutableSortedMap(meta.keySubMap(range.getCCWId(), range.getCWId())));
      }
  }

  public IdSet scan() {
    synchronized (meta){
      return idf.buildIdSet(new ImmutableSortedMap(meta.keyMap()));
    }
  }

  public SortedMap scanMetadata(IdRange range) {
    if (range.isEmpty()) 
      return new RedBlackMap();
    else if (range.getCCWId().equals(range.getCWId())) 
      return scanMetadata();
    else 
      return new ImmutableSortedMap(meta.keySubMap(range.getCCWId(), range.getCWId()));
  }

  /* (non-Javadoc)
   * @see rice.persistence.Catalog#scanMetadata()
   */
  public SortedMap scanMetadata() {
    return new ImmutableSortedMap(meta.keyMap());
  }

  /* (non-Javadoc)
   * @see rice.persistence.Catalog#scanMetadataValuesHead(java.lang.Object)
   */
  public SortedMap scanMetadataValuesHead(Object value) {
    return new ImmutableSortedMap(meta.valueHeadMap(value));
  }

  /* (non-Javadoc)
   * @see rice.persistence.Catalog#scanMetadataValuesNull()
   */
  public SortedMap scanMetadataValuesNull() {
    return new ImmutableSortedMap(meta.valueNullMap());
  }

  public int getSize() {
    synchronized (meta) {
      return meta.size();
    }
  }

  public long getTotalSize() {
    // total number of bytes used
    // not sure if this is net or gross
    // XXX this might be used
    throw new UnsupportedOperationException("getTotalSize unimplemented for DatabaseStorage");
  }

  public void flush(Continuation c) {
    // XXX
    // close databases
    // then truncate them
    // then re-open
    throw new UnsupportedOperationException("flush unimplemented for DatabaseStorage");
  }

  protected DatabaseEntry id2db(Id id) {
    return new DatabaseEntry(id.toByteArray());
  }

  protected Id db2id(DatabaseEntry dbe) {
    return idf.buildId(dbe.getData());
  }

  protected DatabaseEntry dbSerialize(Serializable obj) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream objout = new XMLObjectOutputStream(
          new BufferedOutputStream(new GZIPOutputStream(baos)));
      objout.writeObject(obj);
      return new DatabaseEntry(baos.toByteArray());
    } catch (IOException e) {
      if (logger.level  <= Logger.INFO) logger.logException("Problem serializing "+obj+": ",e);
      throw new RuntimeException("Problem serializing "+obj+": "+e);
    }
  }

  protected Serializable dbDeserialize(DatabaseEntry dbe) {
    try {
      ByteArrayInputStream bais = new ByteArrayInputStream(dbe.getData());
      ObjectInputStream objin = new XMLObjectInputStream(
          new BufferedInputStream(new GZIPInputStream(bais)));
      return (Serializable) objin.readObject();
    } catch (IOException e) {
      if (logger.level  <= Logger.INFO) logger.logException("Problem deserializing: ",e);
      throw new RuntimeException("Problem deserializing: "+e);
    } catch (ClassNotFoundException e) {
      if (logger.level  <= Logger.INFO) logger.logException("Problem deserializing: ",e);
      throw new RuntimeException("Problem deserializing: "+e);
    }
  }
}
