package rice.p2p.glacier;

import rice.p2p.commonapi.Id;
import java.io.Serializable;

public class FragmentKey implements Serializable, Comparable {
    protected Id owner;
    protected Id id;
    
    public FragmentKey(Id owner, Id id)
    {
        this.id = id;
        this.owner = owner;
    }
    
    public Id getOwner()
    {
        return owner;
    }
    
    public Id getId()
    {
        return id;
    }
    
    public boolean equals(Object peer)
    {
        if (!(peer instanceof FragmentKey))
            return false;
            
        FragmentKey fk = (FragmentKey)peer;
        return (fk.owner.equals(this.owner) && fk.id.equals(this.id));
    }
    
    public String toString()
    {
        return owner.toString()+"."+id.toString();
    }

    public String toStringFull()
    {
        return owner.toStringFull()+"-"+id.toStringFull();
    }
    
    public int compareTo(Object o)
    {
        if (((FragmentKey)o).owner == null) {
            System.err.println("other owner = null");
            System.exit(1);
        }

        if (owner == null) {
            System.err.println("my owner = null");
            System.exit(1);
        }

        int ownerResult = owner.compareTo(((FragmentKey)o).owner);
        if (ownerResult != 0)
            return ownerResult;
            
        return id.compareTo(((FragmentKey)o).id);
    }
    
    public int hashCode() {
        return (id.hashCode() + owner.hashCode());
    }
}
