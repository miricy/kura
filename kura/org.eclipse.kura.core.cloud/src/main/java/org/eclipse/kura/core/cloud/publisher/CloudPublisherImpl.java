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

import org.eclipse.kura.KuraErrorCode;
import org.eclipse.kura.KuraException;
import org.eclipse.kura.cloud.CloudService;
import org.eclipse.kura.cloud.publisher.CloudPublisher;
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

public class CloudPublisherImpl implements CloudPublisher, ConfigurableComponent {

    private final class CloudServiceTrackerCustomizer implements ServiceTrackerCustomizer<CloudService, CloudService> {

        @Override
        public CloudService addingService(final ServiceReference<CloudService> reference) {
            CloudService tempCloudService = CloudPublisherImpl.this.bundleContext.getService(reference);

            if (tempCloudService instanceof CloudServiceImpl) {
                CloudPublisherImpl.this.cloudService = (CloudServiceImpl) tempCloudService;
                return tempCloudService;
            } else {
                CloudPublisherImpl.this.bundleContext.ungetService(reference);
            }

            return null;
        }

        @Override
        public void removedService(final ServiceReference<CloudService> reference, final CloudService service) {
            CloudPublisherImpl.this.cloudService = null;
        }

        @Override
        public void modifiedService(ServiceReference<CloudService> reference, CloudService service) {
            // Not needed
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(CloudPublisherImpl.class);

    private ServiceTrackerCustomizer<CloudService, CloudService> cloudServiceTrackerCustomizer;
    private ServiceTracker<CloudService, CloudService> cloudServiceTracker;

    private CloudPublisherOptions cloudPublisherOptions;
    private CloudServiceImpl cloudService;
    private BundleContext bundleContext;

    protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
        logger.debug("Activating Cloud Publisher...");
        this.bundleContext = componentContext.getBundleContext();

        this.cloudPublisherOptions = new CloudPublisherOptions(properties);

        this.cloudServiceTrackerCustomizer = new CloudServiceTrackerCustomizer();
        initCloudServiceTracking();

        logger.debug("Activating Cloud Publisher... Done");
    }

    public void updated(Map<String, Object> properties) {
        logger.debug("Updating Cloud Publisher...");

        this.cloudPublisherOptions = new CloudPublisherOptions(properties);

        if (nonNull(this.cloudServiceTracker)) {
            this.cloudServiceTracker.close();
        }
        initCloudServiceTracking();

        logger.debug("Updating Cloud Publisher... Done");
    }

    protected void deactivate(ComponentContext componentContext) {
        logger.debug("Deactivating Cloud Publisher...");

        if (nonNull(this.cloudServiceTracker)) {
            this.cloudServiceTracker.close();
        }
        logger.debug("Deactivating Cloud Publisher... Done");
    }

    @Override
    public int publish(KuraPayload message) throws KuraException {
        if (this.cloudService == null) {
            logger.info("Null cloud service");
            throw new KuraException(KuraErrorCode.NOT_CONNECTED); // TODO: create a more meaningful one
        }

        DataService dataService = this.cloudService.getDataService();
        CloudServiceOptions cloudServiceOptions = this.cloudService.getCloudServiceOptions();

        String deviceId = cloudServiceOptions.getTopicClientIdToken();
        String appTopic = this.cloudPublisherOptions.getSemanticTopic();
        int qos = this.cloudPublisherOptions.getQos();
        boolean retain = this.cloudPublisherOptions.isRetain();
        int priority = this.cloudPublisherOptions.getPriority();
        byte[] appPayload = this.cloudService.encodePayload(message);
        boolean isControl = MessageType.CONTROL.equals(this.cloudPublisherOptions.getMessageType());
        String fullTopic = encodeTopic(deviceId, appTopic, isControl);

        return dataService.publish(fullTopic, appPayload, qos, retain, priority);
    }

    private void initCloudServiceTracking() {
        String selectedCloudServicePid = this.cloudPublisherOptions.getCloudServicePid();
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

    private String encodeTopic(String deviceId, String appTopic, boolean isControl) {
        CloudServiceOptions options = this.cloudService.getCloudServiceOptions();
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

}
