package org.eclipse.kura.cloud.connection;

import org.eclipse.kura.cloud.connection.listener.CloudConnectionListener;

/**
 * Interface used to track {@link CloudConnectionListener}s.
 * It provides specific methods used to register and unregister listeners interested in receiving events related to the
 * connection status.
 *
 * @since 2.0
 */
public interface CloudConnectionListenerTracker {

    public void register(CloudConnectionListener cloudConnectionListener);

    public void unregister(CloudConnectionListener cloudConnectionListener);

}
