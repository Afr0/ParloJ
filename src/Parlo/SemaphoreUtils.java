/*This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one at
http://mozilla.org/MPL/2.0/.

The Original Code is the Parlo library.

The Initial Developer of the Original Code is
Mats 'Afr0' Vederhus. All Rights Reserved.

Contributor(s): ______________________________________.
*/

package Parlo;
import java.util.concurrent.*;

public class SemaphoreUtils 
{
	/**
	 * Allows consumers to wait asynchronously for a Semaphore instance
	 * to acquire a lock.
	 * @param semaphore The Semaphore with which to acquire a lock.
	 * @return A CompletableFuture that can be awaited with .get()
	 */
    public static CompletableFuture<Void> waitAsync(Semaphore semaphore) 
    {
        return CompletableFuture.runAsync(() -> 
        {
            try 
            {
                semaphore.acquire();
            } 
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt(); // Restore interrupted status
                throw new RuntimeException(e);
            }
        });
    }
}
