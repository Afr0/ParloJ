/*This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one at
http://mozilla.org/MPL/2.0/.

The Original Code is the Parlo library.

The Initial Developer of the Original Code is
Mats 'Afr0' Vederhus. All Rights Reserved.

Contributor(s): ______________________________________.
*/

package Parlo.Packets;

public enum ParloIDs 
{
    Heartbeat(0xFD),
    SGoodbye(0xFE),
    CGoodbye(0xFF);

    private final int ID;

    ParloIDs(int id) 
    {
        this.ID = id;
    }

    public int GetID() 
    {
        return ID;
    }
}
