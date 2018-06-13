/*******************************************************************************
 * Copyright (c) 2016, 2018 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Eurotech
 *  Amit Kumar Mondal
 *  
 *******************************************************************************/
package org.eclipse.kura.internal.wire.subscriber;

import static java.util.Objects.requireNonNull;

import java.util.Map;

final class CloudSubscriberOptions {

    private static final String CLOUD_SUBSCRIBER_PID = "subscriber.pid";

    private final Map<String, Object> properties;

    CloudSubscriberOptions(final Map<String, Object> properties) {
        requireNonNull(properties, "Properties cannot be null");
        this.properties = properties;
    }

    String getCloudSubscriberPid() {
        final Object configCloudServicePid = this.properties.get(CLOUD_SUBSCRIBER_PID);
        if (configCloudServicePid instanceof String) {
            return (String) configCloudServicePid;
        }
        return "";
    }
}