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
import org.eclipse.kura.cloud.subscriber.listener.SubscriberListener;
import org.osgi.annotation.versioning.ProviderType;

/**
 * @since 2.0
 */
@ProviderType
public interface CloudSubscriber extends CloudConnectionListenerTracker{

    public void register(SubscriberListener listener);

    public void unregister(SubscriberListener listener);

}
