package rice.p2p.glacier;

import rice.p2p.glacier.*;
import rice.p2p.commonapi.Id;
import java.util.Date;
import java.io.Serializable;

public class FileInfo implements Serializable {
    public final static int maxHoldersPerFragment = 8;

    public FragmentKey key;
    public StorageManifest manifest;
    public boolean[][] holderKnown;
    public Id[][] holderId;
    public boolean[][] holderDead;
    public boolean[][] holderCertain;
    public Date[][] lastHeard;
    public int[] holderPointer;
    
    public FileInfo(FragmentKey key, StorageManifest manifest, int numFragments)
    {
        this.key = key;
        this.manifest = manifest;
        
        holderKnown = new boolean[numFragments][maxHoldersPerFragment];
        holderId = new Id[numFragments][maxHoldersPerFragment];
        holderDead = new boolean[numFragments][maxHoldersPerFragment];
        holderCertain = new boolean[numFragments][maxHoldersPerFragment];
        lastHeard = new Date[numFragments][maxHoldersPerFragment];
        holderPointer = new int[numFragments];
        
        for (int i=0; i<numFragments; i++) {
            holderPointer[i] = 0;
            for (int j=0; j<maxHoldersPerFragment; j++)
                holderKnown[i][j] = false;
        }
    }
    
    public int getNextAvailableSlotFor(int fragmentID) 
    {
        int triesLeft = maxHoldersPerFragment;
        int i = holderPointer[fragmentID];
        
        while (triesLeft-- > 0) {
            if (!holderKnown[fragmentID][i] || holderDead[fragmentID][i]) {
                holderPointer[fragmentID] = (i+1) % maxHoldersPerFragment;
                return i;
            }
            
            i = (i+1) % maxHoldersPerFragment;
        }
                
        return -1;
    }
    
    public boolean haveFragment(int fragmentID)
    {
        for (int i=0; i<maxHoldersPerFragment; i++)
            if (holderKnown[fragmentID][i] && !holderDead[fragmentID][i] &&
                (holderId[fragmentID][i] == null))
                return true;
        
        return false;
    }
    
    public boolean anyLiveHolder(int fragmentID)
    {
        for (int i=0; i<maxHoldersPerFragment; i++)
            if (holderKnown[fragmentID][i] && !holderDead[fragmentID][i])
                return true;
        
        return false;
    }
};
