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
