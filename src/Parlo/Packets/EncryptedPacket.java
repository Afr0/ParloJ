/*This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one at
http://mozilla.org/MPL/2.0/.

The Original Code is the Parlo library.

The Initial Developer of the Original Code is
Mats 'Afr0' Vederhus. All Rights Reserved.

Contributor(s): ______________________________________.
*/

package Parlo.Packets;

import Parlo.LogLevel;
import Parlo.Logger;
import Parlo.Encryption.*;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.SecretKey;
import java.security.spec.KeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;

public class EncryptedPacket extends Packet 
{
    private EncryptionArgs Args;

    public EncryptedPacket(EncryptionArgs args, byte id, byte[] serializedData) 
    {
        super(id, serializedData, false);

        if (args == null)
            throw new IllegalArgumentException("Args");
        if (serializedData == null)
            throw new IllegalArgumentException("SerializedData");

        this.Args = args;
    }
    
    /**
     * Decrypts the contents of this EncryptedPacket instance.
     * @return An array of bytes containing the decrypted data.
     * @throws UnsupportedOperationException If Twofish is selected as the encryption algorithm,
     * 			as it hasn't been implemented yet.
     */
    public byte[] DecryptPacket() throws Exception 
    {
        switch (Args.Mode) 
        {
            case AES:
            default:
                AES aes = new AES(Args.Key, HexStringToByteArray(Args.Salt));
                return aes.Decrypt(getData());
            case Twofish:
                throw new UnsupportedOperationException("Twofish encryption not supported in standard Java.");
        }
    }

    /**
     * Returns this EncryptedPacket instance as an array of bytes.
     */
    public byte[] BuildPacket()
    {
        byte[] encryptedData = null;

        switch (Args.Mode) 
        {
            case AES:
            default:
            	try
            	{
            		AES aes = new AES(Args.Key, HexStringToByteArray(Args.Salt));
            		encryptedData = aes.Encrypt(getData());
            	}
            	catch(Exception exception)
            	{
            		Logger.log("Error while encrypting packet: " + exception.getMessage(), 
            				LogLevel.error);
            	}
                break;
            case Twofish:
                throw new UnsupportedOperationException("Twofish encryption not supported in standard Java.");
        }

        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(byteOut);
        
        try
        {
	        dataOut.write(getID());
	        dataOut.write(getIsCompressed());
	        dataOut.writeShort(encryptedData.length + PacketHeaders.STANDARD);
	        dataOut.write(encryptedData);
	        dataOut.flush();
        }
        catch(IOException exception)
        {
        	Logger.log("Error while building writing encrypted packet: " + exception.toString(), 
        			LogLevel.error);
        }

        return byteOut.toByteArray();
    }

    /**
     * Converts a hex string to a byte array.
     * @param s The hex string to convert.
     * @return The converted string as a byte array.
     */
    private byte[] HexStringToByteArray(String s) 
    {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    /**
     * Creates a new EncryptedPacket instance from a Packet instance.
     * @param args The args to be used for encryption.
     * @param p The Packet instance from which to create a EncryptedPacket instance.
     * @return The new EncryptedPacket instance.
     */
    public static EncryptedPacket FromPacket(EncryptionArgs args, Packet p) 
    {
        if(args == null)
            throw new IllegalArgumentException("Args");
        if (p == null)
            throw new IllegalArgumentException("Packet");

        return new EncryptedPacket(args, p.getID(), p.getData());
    }
}

