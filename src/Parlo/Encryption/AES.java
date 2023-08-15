/*This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one at
http://mozilla.org/MPL/2.0/.

The Original Code is the Parlo library.

The Initial Developer of the Original Code is
Mats 'Afr0' Vederhus. All Rights Reserved.

Contributor(s): ______________________________________.
*/

package Parlo.Encryption;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

public class AES 
{
    private static final int ITERATION_COUNT = 10000;
    private static final int KEY_LENGTH = 256;
    private Cipher encipher;
    private Cipher decipher;

    public AES(String password, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException,
            NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException 
    {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH);
        SecretKey secretKey = factory.generateSecret(spec);
        SecretKeySpec secret = new SecretKeySpec(secretKey.getEncoded(), "AES");

        encipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        encipher.init(Cipher.ENCRYPT_MODE, secret);
        decipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        decipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(new byte[16]));
    }

    public String Encrypt(String plainText) throws BadPaddingException, IllegalBlockSizeException 
    {
        byte[] encrypted = encipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public byte[] Encrypt(byte[] plainBytes) throws BadPaddingException, IllegalBlockSizeException 
    {
        return encipher.doFinal(plainBytes);
    }

    public String Decrypt(String secureText) throws BadPaddingException, IllegalBlockSizeException 
    {
        byte[] decrypted = decipher.doFinal(Base64.getDecoder().decode(secureText));
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    public byte[] Decrypt(byte[] secureBytes) throws BadPaddingException, IllegalBlockSizeException 
    {
        return decipher.doFinal(secureBytes);
    }
}
