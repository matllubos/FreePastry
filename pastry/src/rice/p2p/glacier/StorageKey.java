package rice.p2p.glacier;

import rice.p2p.commonapi.*;
import rice.p2p.glacier.FragmentKey;

public class StorageKey implements Id {
    protected FragmentKey key;
    protected int id;
    
    public StorageKey(FragmentKey key, int id)
    {
        this.id = id;
        this.key = key;
    }

    public boolean equals(Object peer)
    {
        if (!(peer instanceof StorageKey))
            return false;
            
        StorageKey sk = (StorageKey)peer;
        return (sk.key.equals(this.key) && (sk.id == this.id));
    }
    
    public byte[] toByteArray()
    {
        System.err.println("StorageKey::toByteArray() called");
        System.exit(1);
        return null;
    }

    public boolean isBetween(Id ccw, Id cw)
    {
        System.err.println("StorageKey::isBetween() called");
        System.exit(1);
        return false;
    }
    
    public String toStringFull()
    {
        return key.toStringFull()+":"+id;
    }
    
    public String toString()
    {
        return key.toString()+":"+id;
    }
    
    public Distance longDistanceFromId(Id nid)
    {
        System.err.println("StorageKey::longDistanceFromId() called");
        System.exit(1);
        return null;
    }

    public Distance distanceFromId(Id nid)
    {
        System.err.println("StorageKey::distanceFromId() called");
        System.exit(1);
        return null;
    }
    
    public Id addToId(Distance offset)
    {
        System.err.println("StorageKey::addToId() called");
        System.exit(1);
        return null;
    }

    public boolean clockwise(Id nid)
    {
        System.err.println("StorageKey::clockwise() called");
        System.exit(1);
        return false;
    }
    
    public int compareTo(Object o)
    {
        int keyResult = key.compareTo(((StorageKey)o).key);
        if (keyResult != 0)
            return keyResult;
            
        if (this.id < ((StorageKey)o).id)
            return -1;
        if (this.id > ((StorageKey)o).id)
            return 1;
            
        return 0;
    }
    
    public FragmentKey getFragmentKey()
    {
        return key;
    }
    
    public int getFragmentID()
    {
        return id;
    }

    public int hashCode() {
        return (key.hashCode() + id);
    }
}
