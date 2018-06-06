/*******************************************************************************
 * Copyright (c) 2018 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.kura.core.cloud.subscriber;

import static java.util.Objects.nonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.cloud.connection.CloudConnectionService;
import org.eclipse.kura.cloud.connection.listener.CloudConnectionListener;
import org.eclipse.kura.cloud.subscriber.CloudSubscriber;
import org.eclipse.kura.cloud.subscriber.listener.SubscriberListener;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.core.cloud.CloudServiceImpl;
import org.eclipse.kura.core.cloud.CloudServiceOptions;
import org.eclipse.kura.core.cloud.publisher.MessageType;
import org.eclipse.kura.data.DataService;
import org.eclipse.kura.message.KuraPayload;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudSubscriberImpl implements CloudSubscriber, ConfigurableComponent, CloudConnectionListener {

    private static final String APP_TOPIC_KEY = "appTopic";

    private static final String DEVICE_ID_KEY = "deviceId";

    private final class CloudServiceTrackerCustomizer
            implements ServiceTrackerCustomizer<CloudConnectionService, CloudConnectionService> {

        @Override
        public CloudConnectionService addingService(final ServiceReference<CloudConnectionService> reference) {
            CloudConnectionService tempCloudService = CloudSubscriberImpl.this.bundleContext.getService(reference);

            if (tempCloudService instanceof CloudServiceImpl) {
                CloudSubscriberImpl.this.cloudService = (CloudServiceImpl) tempCloudService;
                CloudSubscriberImpl.this.cloudService.registerSubscriber(CloudSubscriberImpl.this);
                CloudSubscriberImpl.this.cloudService.register((CloudConnectionListener) CloudSubscriberImpl.this);
                subscribe();
                return tempCloudService;
            } else {
                CloudSubscriberImpl.this.bundleContext.ungetService(reference);
            }

            return null;
        }

        @Override
        public void removedService(final ServiceReference<CloudConnectionService> reference,
                final CloudConnectionService service) {
            unsubscribe();
            CloudSubscriberImpl.this.cloudService.unregisterSubscriber(CloudSubscriberImpl.this);
            CloudSubscriberImpl.this.cloudService.unregister((CloudConnectionListener) CloudSubscriberImpl.this);
            CloudSubscriberImpl.this.cloudService = null;
        }

        @Override
        public void modifiedService(ServiceReference<CloudConnectionService> reference,
                CloudConnectionService service) {
            // Not needed
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(CloudSubscriberImpl.class);

    private ServiceTrackerCustomizer<CloudConnectionService, CloudConnectionService> cloudServiceTrackerCustomizer;
    private ServiceTracker<CloudConnectionService, CloudConnectionService> cloudServiceTracker;

    private CloudSubscriberOptions cloudSubscriberOptions;
    private CloudServiceImpl cloudService;
    private BundleContext bundleContext;

    private final Set<SubscriberListener> subscribers = new CopyOnWriteArraySet<>();
    private final Set<CloudConnectionListener> cloudConnectionListeners = new CopyOnWriteArraySet<>();

    protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
        logger.debug("Activating Cloud Publisher...");
        this.bundleContext = componentContext.getBundleContext();

        this.cloudServiceTrackerCustomizer = new CloudServiceTrackerCustomizer();

        doUpdate(properties);

        logger.debug("Activating Cloud Publisher... Done");
    }

    public void updated(Map<String, Object> properties) {
        logger.debug("Updating Cloud Publisher...");

        unsubscribe();

        doUpdate(properties);

        logger.debug("Updating Cloud Publisher... Done");
    }

    protected void deactivate(ComponentContext componentContext) {
        logger.debug("Deactivating Cloud Publisher...");

        if (nonNull(this.cloudServiceTracker)) {
            this.cloudServiceTracker.close();
        }
        logger.debug("Deactivating Cloud Publisher... Done");
    }

    private void doUpdate(Map<String, Object> properties) {
        this.cloudSubscriberOptions = new CloudSubscriberOptions(properties);

        if (nonNull(this.cloudServiceTracker)) {
            this.cloudServiceTracker.close();
        }
        initCloudServiceTracking();
    }

    private void initCloudServiceTracking() {
        String selectedCloudServicePid = this.cloudSubscriberOptions.getCloudServicePid();
        String filterString = String.format("(&(%s=%s)(kura.service.pid=%s))", Constants.OBJECTCLASS,
                CloudConnectionService.class.getName(), selectedCloudServicePid);
        Filter filter = null;
        try {
            filter = this.bundleContext.createFilter(filterString);
        } catch (InvalidSyntaxException e) {
            logger.error("Filter setup exception ", e);
        }
        this.cloudServiceTracker = new ServiceTracker<>(this.bundleContext, filter, this.cloudServiceTrackerCustomizer);
        this.cloudServiceTracker.open();
    }

    private synchronized void unsubscribe() {
        if (this.cloudService == null || this.cloudSubscriberOptions == null) {
            return;
        }

        DataService dataService = this.cloudService.getDataService();

        String fullTopic = getFullTopic();

        try {
            dataService.unsubscribe(fullTopic);
        } catch (KuraException e) {
            logger.info("Failed to unsubscribe");
        }
    }

    private synchronized void subscribe() {
        DataService dataService = this.cloudService.getDataService();

        String fullTopic = getFullTopic();

        int qos = this.cloudSubscriberOptions.getQos();

        try {
            dataService.subscribe(fullTopic, qos);
        } catch (KuraException e) {
            logger.info("Failed to subscribe");
        }
    }

    private String getFullTopic() {
        boolean isControl = MessageType.CONTROL.equals(this.cloudSubscriberOptions.getMessageType());

        CloudServiceOptions cloudServiceOptions = this.cloudService.getCloudServiceOptions();
        String deviceId = cloudServiceOptions.getTopicClientIdToken();

        String appTopic = this.cloudSubscriberOptions.getAppTopic();
        return encodeTopic(deviceId, appTopic, isControl);
    }

    private String encodeTopic(String deviceId, String appTopic, boolean isControl) {
        CloudServiceOptions options = this.cloudService.getCloudServiceOptions();
        StringBuilder sb = new StringBuilder();
        if (isControl) {
            sb.append(options.getTopicControlPrefix()).append(options.getTopicSeparator());
        }

        sb.append(options.getTopicAccountToken()).append(options.getTopicSeparator()).append(deviceId)
                .append(options.getTopicSeparator()).append(this.cloudSubscriberOptions.getAppId());

        if (appTopic != null && !appTopic.isEmpty()) {
            sb.append(options.getTopicSeparator()).append(appTopic);
        }

        return sb.toString();
    }

    @Override
    public void register(SubscriberListener listener) {
        this.subscribers.add(listener);
    }

    @Override
    public void unregister(SubscriberListener listener) {
        this.subscribers.remove(listener);
    }

    @Override
    public void onConnectionEstablished() {
        subscribe();
        this.cloudConnectionListeners.forEach(CloudConnectionListener::onConnectionEstablished);
    }

    public String getAppId() {
        return this.cloudSubscriberOptions.getAppId();
    }

    public void onControlMessageArrived(String deviceId, String appTopic, KuraPayload msg, int qos, boolean retain) {
        notifyMessageArrived(deviceId, appTopic, msg);
    }

    public void onMessageArrived(String deviceId, String appTopic, KuraPayload msg, int qos, boolean retain) {
        notifyMessageArrived(deviceId, appTopic, msg);
    }

    private void notifyMessageArrived(String deviceId, String appTopic, KuraPayload msg) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(DEVICE_ID_KEY, deviceId);
        properties.put(APP_TOPIC_KEY, appTopic);

        this.subscribers.stream().forEach(subscriber -> subscriber.onMessageArrived(properties, msg));
    }

    @Override
    public void onConnectionLost() {
        this.cloudConnectionListeners.forEach(CloudConnectionListener::onConnectionLost);
    }

    @Override
    public void onDisconnected() {
        this.cloudConnectionListeners.forEach(CloudConnectionListener::onDisconnected);
    }

    @Override
    public void register(CloudConnectionListener cloudConnectionListener) {
        this.cloudConnectionListeners.add(cloudConnectionListener);
    }

    @Override
    public void unregister(CloudConnectionListener cloudConnectionListener) {
        this.cloudConnectionListeners.remove(cloudConnectionListener);
    }
}
