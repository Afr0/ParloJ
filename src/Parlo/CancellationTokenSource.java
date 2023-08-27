/*This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one at
http://mozilla.org/MPL/2.0/.

The Original Code is the Parlo library.

The Initial Developer of the Original Code is
Mats 'Afr0' Vederhus. All Rights Reserved.

Contributor(s): ______________________________________.
*/

package Parlo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A token that can be used to cancel tasks.
 */
public class CancellationTokenSource 
{
    private AtomicBoolean isCancellationRequested = new AtomicBoolean(false);

    /**
     * Has cancellation been requested?
     * @return True if it has, false otherwise.
     */
    public boolean isCancellationRequested() 
    {
        return isCancellationRequested.get();
    }

    /**
     * Set the token to indicate cancellation.
     */
    public void cancel() 
    {
        isCancellationRequested.set(true);
    }
}
