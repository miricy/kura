package org.eclipse.kura.cloud.connection;

import org.eclipse.kura.cloud.connection.listener.CloudConnectionListener;

/**
 * @since 2.0
 */
public interface CloudConnectionListenerTracker {

    public void register(CloudConnectionListener cloudConnectionListener);

    public void unregister(CloudConnectionListener cloudConnectionListener);

}
