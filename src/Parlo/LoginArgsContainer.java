/*This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one at
http://mozilla.org/MPL/2.0/.

The Original Code is the Parlo Library.

The Initial Developer of the Original Code is
Mats 'Afr0' Vederhus. All Rights Reserved.

Contributor(s): ______________________________________.
*/

package Parlo;

/**
 * Container for arguments supplied when logging in,
 * to the OnConnected delegate in NetworkClient.cs.
 * AT A MINIMUM, you need to provide an address and a
 * port when connecting to a remote host.
 * This acts as a base class that can be inherited
 * from to accommodate more/different arguments.
 */
public class LoginArgsContainer 
{
	/**
	 * The address of the remote host to connect to.
	 */
    public String Address;

    /**
     * The port of the remote host to connect to.
     */
    public int Port;
    
    /**
     * The client that is connecting.
     */
    public NetworkClient Client;

    /**
     * The username of the client that is connecting.
     * This is optional.
     */
    public String Username;
    
    /**
     * The password of the client that is connecting.
     * This is optional.
     */
    public String Password;
}
