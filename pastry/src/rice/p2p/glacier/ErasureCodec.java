package rice.p2p.glacier;

import java.io.*;
import java.util.Arrays;

public class ErasureCodec {

    static final int Lfield = 10;
    static final int MultField = (1<<Lfield)-1;
    static final int MAX_OBJECT_SIZE = 500000; /* bytes */

    static int[] ExpToFieldElt;
    static int[] FieldEltToExp;
    static boolean isEltInitialized = false;
    
    protected int numFragments;
    protected int numSurvivors;
    
    protected void initElt() 
    {
        final int polymask[] = { 
            0x0000, 0x0003, 0x0007, 0x000B, 0x0013, 0x0025, 0x0043, 0x0083,
            0x011D, 0x0211, 0x0409, 0x0805, 0x1053, 0x201B, 0x402B, 0x8003
        };
        
        ExpToFieldElt = new int[MultField + Lfield];
        
        ExpToFieldElt[0] = 1;
	for (int i=1; i<MultField + Lfield - 1; i++) {
            ExpToFieldElt[i] = ExpToFieldElt[i-1] << 1;
            if ((ExpToFieldElt[i] & (1<<Lfield)) != 0)
                ExpToFieldElt[i] ^= polymask[Lfield];
	}
	
	/* This is the inverter for the previous field. Note that 0
	   is not invertible! */
	
        FieldEltToExp = new int[MultField+1];
        
	FieldEltToExp[0] = -1;
	for (int i=0; i<MultField; i++)
            FieldEltToExp[ExpToFieldElt[i]] = i;
    }
    
    public ErasureCodec(int _numFragments, int _numSurvivors)
    {
        numFragments = _numFragments;
        numSurvivors = _numSurvivors;
        
        if (!isEltInitialized)
            initElt();
    }

    public Fragment[] encode(Serializable obj)
    {
        byte bytes[];
        
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            ObjectOutputStream objectStream = new ObjectOutputStream(byteStream);

            objectStream.writeObject(obj);
            objectStream.flush();

            bytes = byteStream.toByteArray(); 
        
/*            FileOutputStream fos = new FileOutputStream("objOrig");
            fos.write(bytes);
            fos.close();

            System.out.println("Len = "+bytes.length);
*/        
        } catch (IOException ioe) { 
            System.err.println(ioe); 
            return null; 
        }

        if (bytes.length > MAX_OBJECT_SIZE)
          return null;

        int numWords = (bytes.length+3)/4;
        int wordsPerGroup = (numSurvivors*Lfield);
        int numGroups = (numWords+(wordsPerGroup-1))/wordsPerGroup;
        int wordsPerFragment = numGroups * Lfield;
        
        int message[] = new int[numFragments * wordsPerFragment];
        Arrays.fill(message, 0);
        
        int s = bytes.length/4;
        for (int i=0; i<s; i++)
            message[i] = (bytes[4*i]<<24) | ((bytes[4*i+1]<<16)&0xFF0000) | ((bytes[4*i+2]<<8)&0xFF00) | (bytes[4*i+3]&0xFF);

        if (bytes.length > 4*s) {
            message[s] = bytes[4*s]<<24;
            if (bytes.length > (4*s+1))
                message[s] |= (bytes[4*s+1]<<16)&0xFF0000;
            if (bytes.length > (4*s+2))
                message[s] |= (bytes[4*s+2]<<8)&0xFF00;
        }

//        System.out.println(bytes.length+" bytes => "+numFragments+" fragments with "+wordsPerFragment+" words ("+numGroups+" groups)");

        for (int g=0; g<numGroups; g++) {
            for (int row=0; row<(numFragments-numSurvivors); row++) {
                for (int col=0; col<numSurvivors; col++) {
                    int exponent = (MultField - FieldEltToExp[(row^col)^(1<<(Lfield-1))]) % MultField;
                    for (int row_bit = 0; row_bit<Lfield; row_bit++) {
                        for (int col_bit = 0; col_bit<Lfield; col_bit++) {
                            if ((ExpToFieldElt[exponent+row_bit]&(1<<col_bit)) != 0)
                                message[(numSurvivors*Lfield + row*Lfield + row_bit)*numGroups + g] ^= 
                                    message[(col_bit+col*Lfield)*numGroups + g];
                        }
                    }
                }
            }
        }

/*    
        try {

            FileOutputStream gos = new FileOutputStream("objReco");
            DataOutputStream dos = new DataOutputStream(gos);
            for (int i=0; i<message.length; i++)
                dos.writeInt(message[i]);
            dos.flush();
            gos.close();
        } catch (IOException ioe) {
            System.err.println(ioe);
            return null;
        }
*/        
        Fragment frag[] = new Fragment[numFragments];
        for (int i=0; i<numFragments; i++)
            frag[i] = new Fragment(i, wordsPerFragment);
        
        for (int i=0; i<numFragments; i++)
            for (int j=0; j<wordsPerFragment; j++)
                frag[i].payload[j] = message[i*wordsPerFragment + j];
                
        return frag;
    }

    public Object decode(Fragment frag[])
    {
        int wordsPerFragment = frag[0].payload.length;
        int message[] = new int[numFragments * wordsPerFragment];
        int numGroups = wordsPerFragment / Lfield;
        Arrays.fill(message, 0);

        boolean haveFragment[] = new boolean[numFragments];
        Arrays.fill(haveFragment, false);
        
        for (int i=0; i<frag.length; i++) {
            haveFragment[frag[i].fragmentID] = true;
            for (int j=0; j<wordsPerFragment; j++)
                message[frag[i].fragmentID*wordsPerFragment + j] = frag[i].payload[j];
        }

        int ColInd[] = new int[numSurvivors];
        int RowInd[] = new int[numFragments - numSurvivors];
        
        int nMissing = 0, nExtra = 0;
        for (int i=0; i<numSurvivors; i++)
            if (!haveFragment[i])
                ColInd[nMissing++] = i;
        for (int i=0; i<(numFragments-numSurvivors); i++)
            if (haveFragment[numSurvivors + i])
                RowInd[nExtra++] = i;

        if (nMissing > nExtra)
            return null;
            
        if (nMissing < nExtra)
            nExtra = nMissing;

        int[] C, D, E, F;
        C = new int[numFragments - numSurvivors];
        Arrays.fill(C, 0);
        D = new int[numFragments - numSurvivors];
        Arrays.fill(D, 0);
        E = new int[numFragments - numSurvivors];
        Arrays.fill(E, 0);
        F = new int[numFragments - numSurvivors];
        Arrays.fill(F, 0);
        
        for (int row=0; row<nExtra; row++) {
            for (int col=0; col<nExtra; col++) {
                if (col!=row) {
                    C[row] += FieldEltToExp[RowInd[row] ^ RowInd[col]];
                    D[row] += FieldEltToExp[ColInd[row] ^ ColInd[col]];
                }
			
                E[row] += FieldEltToExp[RowInd[row] ^ ColInd[col] ^ (1<<(Lfield-1))];
                F[col] += FieldEltToExp[RowInd[row] ^ ColInd[col] ^ (1<<(Lfield-1))];
            }
        }

	long InvMat[][] = new long[nExtra][nExtra];
		
        for (int row=0; row<nExtra; row++) {
            for (int col=0; col<nExtra; col++) {
                InvMat[row][col] = E[col] + F[row] - C[col] - D[row] -
                    FieldEltToExp[RowInd[col] ^ ColInd[row] ^ (1<<(Lfield-1))];
                if (InvMat[row][col] >= 0)
                    InvMat[row][col] = InvMat[row][col] % MultField;
                else
                    InvMat[row][col] = (MultField - (-InvMat[row][col] % MultField)) % MultField;
            }
        }
	
	// *** Second last step ***
	
        int M[] = new int[(numFragments-numSurvivors)*Lfield*numGroups];
        for (int g=0; g<numGroups; g++) {
            for (int i=0; i<nExtra; i++) 
                for (int j=0; j<Lfield; j++)
                    M[(i*Lfield + j)*numGroups + g] = message[(j + (RowInd[i]+numSurvivors)*Lfield)*numGroups + g];
	
            for (int row=0; row<nExtra; row++) {
                for (int col=0; col<numSurvivors; col++) {
                    if (haveFragment[col]) {
                        int exponent = (MultField - FieldEltToExp[RowInd[row] ^ col ^ (1<<(Lfield-1))]) % MultField;
                        for (int row_bit = 0; row_bit<Lfield; row_bit++) {
                            for (int col_bit = 0; col_bit<Lfield; col_bit++) {
                                if ((ExpToFieldElt[exponent+row_bit] & (1<<col_bit)) != 0)
                                    M[(row_bit + row*Lfield)*numGroups + g] ^= message[(col_bit + col*Lfield)*numGroups + g];
                            }
                        }
                    }
                }
            }

            // *** Last step ***
	
            for (int row=0; row<nExtra; row++) {
                for (int col=0; col<nExtra; col++) {
                    int exponent = (int)InvMat[row][col];
                    for (int row_bit = 0; row_bit<Lfield; row_bit++) {
                        for (int col_bit=0; col_bit<Lfield; col_bit++) {
                            if ((ExpToFieldElt[exponent + row_bit] & (1<<col_bit)) != 0) {
                                message[(row_bit + ColInd[row]*Lfield)*numGroups + g] ^=
                                    M[(col_bit + col*Lfield)*numGroups + g];
                            }
                        }
                    }
                }
            }
        }

        byte[] bytes = new byte[numSurvivors * wordsPerFragment * 4];
        for (int i=0; i<(bytes.length/4); i++) {
            bytes[4*i+0] = (byte)(message[i] >> 24);
            bytes[4*i+1] = (byte)((message[i] >> 16)&0xFF);
            bytes[4*i+2] = (byte)((message[i] >>  8)&0xFF);
            bytes[4*i+3] = (byte)(message[i] & 0xFF);
        }

/*        try {
            FileOutputStream gos = new FileOutputStream("objXXX");
            DataOutputStream dos = new DataOutputStream(gos);
            for (int i=0; i<message.length; i++)
                dos.writeInt(message[i]);
            dos.flush();
            gos.close();
        } catch (IOException ioe) {
            System.err.println(ioe);
            return null;
        } */

        try {
            ByteArrayInputStream byteinput = new ByteArrayInputStream(bytes);
            ObjectInputStream objectInput = new ObjectInputStream(byteinput);

            return objectInput.readObject();
        } catch (IOException ioe) {
            System.err.println(ioe);
        } catch (ClassNotFoundException cnfe) {
            System.err.println(cnfe);
        }

        return null;        
    }
    
    public static void main(String args[])
    {
        ErasureCodec codec = new ErasureCodec(30, 4);
        Serializable s = new String("Habe Mut, Dich Deines eigenen Verstandes zu bedienen! Aufklaerung ist der Ausgang aus Deiner selbstverschuldeten Unmuendigkeit! 12345678dsksclsncksjncksj");

        Fragment frag[] = codec.encode(s);
        
        Fragment frag2[] = new Fragment[4];
        frag2[0] = frag[9];
        frag2[1] = frag[11];
        frag2[2] = frag[12];
        frag2[3] = frag[17];
        
        Object d = codec.decode(frag2);
        
        System.out.println(d);
    }
}
