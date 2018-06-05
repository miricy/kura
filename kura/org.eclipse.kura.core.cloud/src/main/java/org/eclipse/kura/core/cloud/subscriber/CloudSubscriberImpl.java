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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.cloud.CloudClientListener;
import org.eclipse.kura.cloud.CloudService;
import org.eclipse.kura.cloud.subscriber.CloudSubscriber;
import org.eclipse.kura.cloud.subscriber.listener.SubscriberListener;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.core.cloud.CloudServiceImpl;
import org.eclipse.kura.core.cloud.CloudServiceOptions;
import org.eclipse.kura.core.cloud.publisher.CloudPublisherImpl;
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

public class CloudSubscriberImpl implements CloudSubscriber, ConfigurableComponent, CloudClientListener {

    private final class CloudServiceTrackerCustomizer implements ServiceTrackerCustomizer<CloudService, CloudService> {

        @Override
        public CloudService addingService(final ServiceReference<CloudService> reference) {
            CloudService tempCloudService = CloudSubscriberImpl.this.bundleContext.getService(reference);

            if (tempCloudService instanceof CloudServiceImpl) {
                CloudSubscriberImpl.this.cloudService = (CloudServiceImpl) tempCloudService;
                CloudSubscriberImpl.this.cloudService.registerSubscriber(CloudSubscriberImpl.this);
                subscribe();
                return tempCloudService;
            } else {
                CloudSubscriberImpl.this.bundleContext.ungetService(reference);
            }

            return null;
        }

        @Override
        public void removedService(final ServiceReference<CloudService> reference, final CloudService service) {
            unsubscribe();
            CloudSubscriberImpl.this.cloudService.unregisterSubscriber(CloudSubscriberImpl.this);
            CloudSubscriberImpl.this.cloudService = null;
        }

        @Override
        public void modifiedService(ServiceReference<CloudService> reference, CloudService service) {
            // Not needed
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(CloudPublisherImpl.class);

    private ServiceTrackerCustomizer<CloudService, CloudService> cloudServiceTrackerCustomizer;
    private ServiceTracker<CloudService, CloudService> cloudServiceTracker;

    private CloudSubscriberOptions cloudSubscriberOptions;
    private CloudServiceImpl cloudService;
    private BundleContext bundleContext;

    private final Set<SubscriberListener> subscribers = new CopyOnWriteArraySet<>();

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
                CloudService.class.getName(), selectedCloudServicePid);
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
        this.subscribers.stream().forEach(SubscriberListener::onConnectionEstablished);
    }

    @Override
    public void onMessagePublished(int messageId, String topic) {
        // Not needed
    }

    @Override
    public void onMessageConfirmed(int messageId, String topic) {
        // Not needed
    }

    public String getAppId() {
        return this.cloudSubscriberOptions.getAppId();
    }

    @Override
    public void onControlMessageArrived(String deviceId, String appTopic, KuraPayload msg, int qos, boolean retain) {
        this.subscribers.stream().forEach(subscriber -> subscriber.onMessageArrived(null, msg)); //TODO
    }

    @Override
    public void onMessageArrived(String deviceId, String appTopic, KuraPayload msg, int qos, boolean retain) {
        this.subscribers.stream().forEach(subscriber -> subscriber.onMessageArrived(null, msg)); //TODO
    }

    @Override
    public void onConnectionLost() {
        this.subscribers.stream().forEach(SubscriberListener::onConnectionLost);
    }
}
