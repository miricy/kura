/*******************************************************************************
 * Copyright (c) 2018 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.kura.core.cloud.publisher;

import static java.util.Objects.nonNull;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.kura.KuraErrorCode;
import org.eclipse.kura.KuraException;
import org.eclipse.kura.cloudconnection.CloudConnectionService;
import org.eclipse.kura.cloudconnection.listener.CloudConnectionListener;
import org.eclipse.kura.cloudconnection.publisher.CloudPublisher;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.core.cloud.CloudServiceImpl;
import org.eclipse.kura.core.cloud.CloudServiceOptions;
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

public class CloudPublisherImpl implements CloudPublisher, ConfigurableComponent, CloudConnectionListener {

    private final class CloudServiceTrackerCustomizer
            implements ServiceTrackerCustomizer<CloudConnectionService, CloudConnectionService> {

        @Override
        public CloudConnectionService addingService(final ServiceReference<CloudConnectionService> reference) {
            CloudConnectionService tempCloudService = CloudPublisherImpl.this.bundleContext.getService(reference);

            if (tempCloudService instanceof CloudServiceImpl) {
                CloudPublisherImpl.this.cloudServiceImpl = (CloudServiceImpl) tempCloudService;
                CloudPublisherImpl.this.cloudServiceImpl.register(CloudPublisherImpl.this);
                return tempCloudService;
            } else {
                CloudPublisherImpl.this.bundleContext.ungetService(reference);
            }

            return null;
        }

        @Override
        public void removedService(final ServiceReference<CloudConnectionService> reference,
                final CloudConnectionService service) {
            CloudPublisherImpl.this.cloudServiceImpl.unregister(CloudPublisherImpl.this);
            CloudPublisherImpl.this.cloudServiceImpl = null;
        }

        @Override
        public void modifiedService(ServiceReference<CloudConnectionService> reference,
                CloudConnectionService service) {
            // Not needed
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(CloudPublisherImpl.class);

    private static final String TOPIC_PATTERN_STRING = "\\$([^\\s/]+)";
    private static final Pattern TOPIC_PATTERN = Pattern.compile(TOPIC_PATTERN_STRING);

    private ServiceTrackerCustomizer<CloudConnectionService, CloudConnectionService> cloudConnectionServiceTrackerCustomizer;
    private ServiceTracker<CloudConnectionService, CloudConnectionService> cloudConnectionServiceTracker;

    private CloudPublisherOptions cloudPublisherOptions;
    private CloudServiceImpl cloudServiceImpl;
    private BundleContext bundleContext;

    private final Set<CloudConnectionListener> cloudConnectionListeners = new CopyOnWriteArraySet<>();

    protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
        logger.debug("Activating Cloud Publisher...");
        this.bundleContext = componentContext.getBundleContext();

        this.cloudPublisherOptions = new CloudPublisherOptions(properties);

        this.cloudConnectionServiceTrackerCustomizer = new CloudServiceTrackerCustomizer();
        initCloudServiceTracking();

        logger.debug("Activating Cloud Publisher... Done");
    }

    public void updated(Map<String, Object> properties) {
        logger.debug("Updating Cloud Publisher...");

        this.cloudPublisherOptions = new CloudPublisherOptions(properties);

        if (nonNull(this.cloudConnectionServiceTracker)) {
            this.cloudConnectionServiceTracker.close();
        }
        initCloudServiceTracking();

        logger.debug("Updating Cloud Publisher... Done");
    }

    protected void deactivate(ComponentContext componentContext) {
        logger.debug("Deactivating Cloud Publisher...");

        if (nonNull(this.cloudConnectionServiceTracker)) {
            this.cloudConnectionServiceTracker.close();
        }
        logger.debug("Deactivating Cloud Publisher... Done");
    }

    @Override
    public int publish(KuraPayload message) throws KuraException {
        if (this.cloudServiceImpl == null) {
            logger.info("Null cloud service");
            throw new KuraException(KuraErrorCode.NOT_CONNECTED); // TODO: create a more meaningful one
        }

        DataService dataService = this.cloudServiceImpl.getDataService();
        CloudServiceOptions cloudServiceOptions = this.cloudServiceImpl.getCloudServiceOptions();

        String deviceId = cloudServiceOptions.getTopicClientIdToken();
        String appTopic = buildPublishAppTopic(message);
        int qos = this.cloudPublisherOptions.getQos();
        boolean retain = this.cloudPublisherOptions.isRetain();
        int priority = this.cloudPublisherOptions.getPriority();
        byte[] appPayload = this.cloudServiceImpl.encodePayload(message);
        boolean isControl = MessageType.CONTROL.equals(this.cloudPublisherOptions.getMessageType());
        String fullTopic = encodeTopic(deviceId, appTopic, isControl);

        return dataService.publish(fullTopic, appPayload, qos, retain, priority);
    }

    private void initCloudServiceTracking() {
        String selectedCloudServicePid = this.cloudPublisherOptions.getCloudServicePid();
        String filterString = String.format("(&(%s=%s)(kura.service.pid=%s))", Constants.OBJECTCLASS,
                CloudConnectionService.class.getName(), selectedCloudServicePid);
        Filter filter = null;
        try {
            filter = this.bundleContext.createFilter(filterString);
        } catch (InvalidSyntaxException e) {
            logger.error("Filter setup exception ", e);
        }
        this.cloudConnectionServiceTracker = new ServiceTracker<>(this.bundleContext, filter,
                this.cloudConnectionServiceTrackerCustomizer);
        this.cloudConnectionServiceTracker.open();
    }

    private String encodeTopic(String deviceId, String appTopic, boolean isControl) {
        CloudServiceOptions options = this.cloudServiceImpl.getCloudServiceOptions();
        StringBuilder sb = new StringBuilder();
        if (isControl) {
            sb.append(options.getTopicControlPrefix()).append(options.getTopicSeparator());
        }

        sb.append(options.getTopicAccountToken()).append(options.getTopicSeparator()).append(deviceId)
                .append(options.getTopicSeparator()).append(this.cloudPublisherOptions.getAppId());

        if (appTopic != null && !appTopic.isEmpty()) {
            sb.append(options.getTopicSeparator()).append(appTopic);
        }

        return sb.toString();
    }

    private String buildPublishAppTopic(KuraPayload message) {
        Matcher matcher = TOPIC_PATTERN.matcher(this.cloudPublisherOptions.getAppTopic());
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            Map<String, Object> properties = message.metrics();
            if (properties.containsKey(matcher.group(1))) {
                String replacement = matcher.group(0);

                Object value = properties.get(matcher.group(1));
                if (replacement != null) {
                    matcher.appendReplacement(buffer, value.toString());
                }
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    @Override
    public void onDisconnected() {
        this.cloudConnectionListeners.forEach(CloudConnectionListener::onDisconnected);
    }

    @Override
    public void onConnectionLost() {
        this.cloudConnectionListeners.forEach(CloudConnectionListener::onConnectionLost);
    }

    @Override
    public void onConnectionEstablished() {
        this.cloudConnectionListeners.forEach(CloudConnectionListener::onConnectionEstablished);
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
