/*This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one at
http://mozilla.org/MPL/2.0/.

The Original Code is the Parlo library.

The Initial Developer of the Original Code is
Mats 'Afr0' Vederhus. All Rights Reserved.

Contributor(s): ______________________________________.
*/

package Parlo.Packets;

import java.io.*;
import java.time.Duration;
import java.time.Instant;

public class HeartbeatPacket implements Serializable 
{
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Duration m_TimeSinceLast;
    private Instant m_SentTimestamp;

    public HeartbeatPacket(Duration TimeSinceLast) 
    {
        this.m_TimeSinceLast = TimeSinceLast;
        this.m_SentTimestamp = Instant.now();
    }

    public Duration GetTimeSinceLast() 
    {
        return m_TimeSinceLast;
    }

    public Instant GetSentTimestamp() 
    {
        return m_SentTimestamp;
    }

    public byte[] ToByteArray() 
    {
        try 
        {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(this);
            oos.flush();
            return bos.toByteArray();
        } 
        catch (IOException ex) 
        {
            ex.printStackTrace();
            return null;
        }
    }

    public static HeartbeatPacket ByteArrayToObject(byte[] ArrBytes) 
    {
        try 
        {
            ByteArrayInputStream bis = new ByteArrayInputStream(ArrBytes);
            ObjectInputStream ois = new ObjectInputStream(bis);
            return (HeartbeatPacket) ois.readObject();
        } 
        catch (IOException | ClassNotFoundException ex) 
        {
            ex.printStackTrace();
            return null;
        }
    }
}