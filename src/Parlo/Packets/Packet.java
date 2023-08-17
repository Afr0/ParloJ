/*This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one at
http://mozilla.org/MPL/2.0/.

The Original Code is the Parlo library.

The Initial Developer of the Original Code is
Mats 'Afr0' Vederhus. All Rights Reserved.

Contributor(s): ______________________________________.
*/

package Parlo.Packets;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Packet 
{
    private byte m_ID;
    private byte m_IsCompressed = 0;
    private byte m_IsReliable = 0;
    private short m_Length;
    protected byte[] m_Data;
    private boolean m_IsUDP = false;

    public Packet(byte ID, byte[] SerializedData, boolean IsPacketCompressed) 
    {
        if (SerializedData == null) 
            throw new IllegalArgumentException("SerializedData cannot be null!");

        m_ID = ID;
        m_IsCompressed = (byte) (IsPacketCompressed ? 1 : 0);
        m_Length = (short) (PacketHeaders.STANDARD + SerializedData.length);
        m_Data = SerializedData;
    }

    public Packet(byte ID, byte[] SerializedData, boolean IsPacketCompressed, boolean IsPacketReliable) 
    {
        if (SerializedData == null)
            throw new IllegalArgumentException("SerializedData cannot be null!");

        m_IsUDP = true;
        m_ID = ID;
        m_IsCompressed = (byte) (IsPacketCompressed ? 1 : 0);
        m_IsReliable = (byte) (IsPacketReliable ? 1 : 0);
        m_Length = (short) (PacketHeaders.UDP + SerializedData.length);
        m_Data = SerializedData;
    }

    public Packet(int SequenceNumber, byte ID, byte[] SerializedData, boolean IsPacketCompressed, boolean IsPacketReliable) 
    {
        if (SerializedData == null)
            throw new IllegalArgumentException("SerializedData cannot be null!");

        m_IsUDP = true;
        m_ID = ID;
        m_IsCompressed = (byte) (IsPacketCompressed ? 1 : 0);
        m_IsReliable = (byte) (IsPacketReliable ? 1 : 0);
        m_Length = (short) (PacketHeaders.UDP + SerializedData.length);
        m_Data = SerializedData;
    }

    public byte getID() 
    {
        return m_ID;
    }

    public byte getIsCompressed() 
    {
        return m_IsCompressed;
    }

    public short getLength() 
    {
        return m_Length;
    }

    public byte[] getData() 
    {
        return m_Data;
    }

    public byte[] buildPacket() 
    {
        ByteBuffer buffer;

        if (!m_IsUDP) 
        {
            buffer = ByteBuffer.allocate(4 + m_Data.length);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.put(m_ID);
            buffer.put(m_IsCompressed);
            buffer.putShort(m_Length);
        } 
        else 
        {
            buffer = ByteBuffer.allocate(5 + m_Data.length);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.put(m_ID);
            buffer.put(m_IsCompressed);
            buffer.put(m_IsReliable);
            buffer.putShort(m_Length);
        }

        buffer.put(m_Data);

        return buffer.array();
    }
}
