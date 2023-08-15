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
