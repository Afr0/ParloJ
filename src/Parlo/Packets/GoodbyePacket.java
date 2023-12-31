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

/**
 * An internal class sent by the client and server before disconnecting.
 */
public class GoodbyePacket implements Serializable 
{
	private static final long serialVersionUID = 1L;
	private Duration timeOut;
    private Instant sentTime;

    public GoodbyePacket(int timeOut) 
    {
        this.timeOut = Duration.ofSeconds(timeOut);
        this.sentTime = Instant.now();
    }

    public Duration getTimeOut() 
    {
        return timeOut;
    }

    public Instant getSentTime() 
    {
        return sentTime;
    }

    public byte[] toByteArray() 
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

    public static GoodbyePacket byteArrayToObject(byte[] arrBytes) 
    {
        try 
        {
            ByteArrayInputStream Bis = new ByteArrayInputStream(arrBytes);
            ObjectInputStream Ois = new ObjectInputStream(Bis);
            return (GoodbyePacket) Ois.readObject();
        } 
        catch (IOException | ClassNotFoundException ex) 
        {
            ex.printStackTrace();
            return null;
        }
    }
}





