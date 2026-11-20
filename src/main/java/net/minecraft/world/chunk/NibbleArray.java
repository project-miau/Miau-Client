package net.minecraft.world.chunk;

import java.util.Arrays;

public class NibbleArray
{
     



    private static final byte[] ZERO = new byte[2048];
    private static final byte[] FULL = new byte[2048];
    private byte[] data;

    static
    {
        Arrays.fill(FULL, (byte)-1);
    }

    public NibbleArray()
    {
        this.data = ZERO;
    }

    public NibbleArray(byte[] storageArray)
    {
        if (storageArray.length != 2048)
        {
            throw new IllegalArgumentException("ChunkNibbleArrays should be 2048 bytes not: " + storageArray.length);
        }

        this.data = storageArray;
        this.compact();
    }

     


    public int get(int x, int y, int z)
    {
        return this.getFromIndex(this.getCoordinateIndex(x, y, z));
    }

     


    public void set(int x, int y, int z, int value)
    {
        this.setIndex(this.getCoordinateIndex(x, y, z), value);
    }

    private int getCoordinateIndex(int x, int y, int z)
    {
        return y << 8 | z << 4 | x;
    }

    public int getFromIndex(int index)
    {
        int i = this.getNibbleIndex(index);
        return this.isLowerNibble(index) ? this.data[i] & 15 : this.data[i] >> 4 & 15;
    }

    public void setIndex(int index, int value)
    {
        value &= 15;
        if (this.getFromIndex(index) == value)
        {
            return;
        }

        if (this.data == ZERO || this.data == FULL)
        {
            this.data = this.data.clone();
        }

        int i = this.getNibbleIndex(index);

        if (this.isLowerNibble(index))
        {
            this.data[i] = (byte)(this.data[i] & 240 | value & 15);
        }
        else
        {
            this.data[i] = (byte)(this.data[i] & 15 | (value & 15) << 4);
        }
    }

    private boolean isLowerNibble(int index)
    {
        return (index & 1) == 0;
    }

    private int getNibbleIndex(int index)
    {
        return index >> 1;
    }

    public byte[] getData()
    {
        if (this.data == ZERO || this.data == FULL)
        {
            this.data = this.data.clone();
        }

        return this.data;
    }

    public byte[] getReadOnlyData()
    {
        return this.data;
    }

    public void compact()
    {
        if (this.data != ZERO && Arrays.equals(this.data, ZERO))
        {
            this.data = ZERO;
        }
        else if (this.data != FULL && Arrays.equals(this.data, FULL))
        {
            this.data = FULL;
        }
    }
}
