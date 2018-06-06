/*******************************************************************************
 * Copyright (c) 2018 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.kura.cloud.subscriber;

import org.eclipse.kura.cloud.connection.CloudConnectionListenerTracker;
import org.eclipse.kura.cloud.connection.CloudConnectionService;
import org.eclipse.kura.cloud.subscriber.listener.CloudSubscriberListener;
import org.osgi.annotation.versioning.ProviderType;

/**
 * Interface intended to have a specific implementation associated to a {@link CloudConnectionService} and with
 * specificities related to the targeted cloud provider.
 * The {@link CloudSubscriber} interface is an abstraction on top of the {@link CloudConnectionService} to simplify the
 * subscription and notification process, for each application running in the framework.
 * When an application wants to receive a message from the cloud, it has to take a {@link CloudSubscriber} instance and
 * register itself as a {@link CloudSubscriberListener}, in order to be notified when a message is received by the
 * subscriber.
 *
 * @since 2.0
 *
 * @noimplement This interface is not intended to be implemented by clients.
 */
@ProviderType
public interface CloudSubscriber extends CloudConnectionListenerTracker {

    public void register(CloudSubscriberListener listener);

    public void unregister(CloudSubscriberListener listener);

}
