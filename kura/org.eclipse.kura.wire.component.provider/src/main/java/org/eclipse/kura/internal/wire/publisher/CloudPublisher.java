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
package org.eclipse.kura.internal.wire.publisher;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.kura.cloud.CloudClientListener;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.message.KuraPayload;
import org.eclipse.kura.message.KuraPosition;
import org.eclipse.kura.position.NmeaPosition;
import org.eclipse.kura.position.PositionService;
import org.eclipse.kura.type.TypedValue;
import org.eclipse.kura.wire.WireComponent;
import org.eclipse.kura.wire.WireEnvelope;
import org.eclipse.kura.wire.WireHelperService;
import org.eclipse.kura.wire.WireReceiver;
import org.eclipse.kura.wire.WireRecord;
import org.eclipse.kura.wire.WireSupport;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.wireadmin.Wire;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class CloudPublisher is the specific Wire Component to publish a list of
 * {@link WireRecord}s as received in {@link WireEnvelope} to the configured cloud
 * platform.<br/>
 * <br/>
 *
 * For every {@link WireRecord} as found in {@link WireEnvelope} will be wrapped inside a Kura
 * Payload and will be sent to the Cloud Platform.
 */
public final class CloudPublisher implements WireReceiver, CloudClientListener, ConfigurableComponent {

    /**
     * Inner class defined to track the CloudServices as they get added, modified or removed.
     * Specific methods can refresh the cloudService definition and setup again the Cloud Client.
     *
     */
    private final class CloudPublisherServiceTrackerCustomizer implements
            ServiceTrackerCustomizer<org.eclipse.kura.cloud.publisher.CloudPublisher, org.eclipse.kura.cloud.publisher.CloudPublisher> {

        @Override
        public org.eclipse.kura.cloud.publisher.CloudPublisher addingService(
                final ServiceReference<org.eclipse.kura.cloud.publisher.CloudPublisher> reference) {
            CloudPublisher.this.cloudPublisher = CloudPublisher.this.bundleContext.getService(reference);

            return CloudPublisher.this.cloudPublisher;
        }

        @Override
        public void modifiedService(final ServiceReference<org.eclipse.kura.cloud.publisher.CloudPublisher> reference,
                final org.eclipse.kura.cloud.publisher.CloudPublisher service) {
            CloudPublisher.this.cloudPublisher = CloudPublisher.this.bundleContext.getService(reference);
        }

        @Override
        public void removedService(final ServiceReference<org.eclipse.kura.cloud.publisher.CloudPublisher> reference,
                final org.eclipse.kura.cloud.publisher.CloudPublisher service) {
            CloudPublisher.this.cloudPublisher = null;
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(CloudPublisher.class);

    private BundleContext bundleContext;

    private ServiceTrackerCustomizer<org.eclipse.kura.cloud.publisher.CloudPublisher, org.eclipse.kura.cloud.publisher.CloudPublisher> cloudPublisherTrackerCustomizer;

    private ServiceTracker<org.eclipse.kura.cloud.publisher.CloudPublisher, org.eclipse.kura.cloud.publisher.CloudPublisher> cloudPublisherTracker;

    private volatile org.eclipse.kura.cloud.publisher.CloudPublisher cloudPublisher;

    private CloudPublisherOptions cloudPublisherOptions;

    private volatile WireHelperService wireHelperService;
    private PositionService positionService;

    private WireSupport wireSupport;

    // ----------------------------------------------------------------
    //
    // Dependencies
    //
    // ----------------------------------------------------------------

    /**
     * Binds the Wire Helper Service.
     *
     * @param wireHelperService
     *            the new Wire Helper Service
     */
    public void bindWireHelperService(final WireHelperService wireHelperService) {
        if (isNull(this.wireHelperService)) {
            this.wireHelperService = wireHelperService;
        }
    }

    /**
     * Unbinds the Wire Helper Service.
     *
     * @param wireHelperService
     *            the new Wire Helper Service
     */
    public void unbindWireHelperService(final WireHelperService wireHelperService) {
        if (this.wireHelperService == wireHelperService) {
            this.wireHelperService = null;
        }
    }

    public void setPositionService(PositionService positionService) {
        this.positionService = positionService;
    }

    public void unsetPositionService(PositionService positionService) {
        this.positionService = null;
    }

    // ----------------------------------------------------------------
    //
    // Activation APIs
    //
    // ----------------------------------------------------------------

    /**
     * OSGi Service Component callback for activation.
     *
     * @param componentContext
     *            the component context
     * @param properties
     *            the properties
     */
    protected void activate(final ComponentContext componentContext, final Map<String, Object> properties) {
        logger.debug("Activating Cloud Publisher Wire Component...");
        this.wireSupport = this.wireHelperService.newWireSupport(this,
                (ServiceReference<WireComponent>) componentContext.getServiceReference());
        this.bundleContext = componentContext.getBundleContext();

        // Update properties
        this.cloudPublisherOptions = new CloudPublisherOptions(properties);

        this.cloudPublisherTrackerCustomizer = new CloudPublisherServiceTrackerCustomizer();
        initCloudPublisherTracking();

        logger.debug("Activating Cloud Publisher Wire Component... Done");
    }

    /**
     * OSGi Service Component callback for updating.
     *
     * @param properties
     *            the updated properties
     */
    public void updated(final Map<String, Object> properties) {
        logger.debug("Updating Cloud Publisher Wire Component...");
        // Update properties
        this.cloudPublisherOptions = new CloudPublisherOptions(properties);

        if (nonNull(this.cloudPublisherTracker)) {
            this.cloudPublisherTracker.close();
        }
        initCloudPublisherTracking();

        logger.debug("Updating Cloud Publisher Wire Component... Done");
    }

    /**
     * OSGi Service Component callback for deactivation.
     *
     * @param componentContext
     *            the component context
     */
    protected void deactivate(final ComponentContext componentContext) {
        logger.debug("Deactivating Cloud Publisher Wire Component...");

        if (nonNull(this.cloudPublisherTracker)) {
            this.cloudPublisherTracker.close();
        }
        logger.debug("Deactivating Cloud Publisher Wire Component... Done");
    }

    /** {@inheritDoc} */
    @Override
    public void onConnectionEstablished() {
        // Not required
    }

    /** {@inheritDoc} */
    @Override
    public void onMessageConfirmed(final int messageId, final String topic) {
        // Not required
    }

    /** {@inheritDoc} */
    @Override
    public void onMessagePublished(final int messageId, final String topic) {
        // Not required
    }

    /** {@inheritDoc} */
    @Override
    public void onWireReceive(final WireEnvelope wireEnvelope) {
        requireNonNull(wireEnvelope, "Wire Envelope cannot be null");

        if (nonNull(this.cloudPublisher)) {
            final List<WireRecord> records = wireEnvelope.getRecords();
            publish(records);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void producersConnected(final Wire[] wires) {
        requireNonNull(wires, "Wires cannot be null");
        this.wireSupport.producersConnected(wires);
    }

    /** {@inheritDoc} */
    @Override
    public void updated(final Wire wire, final Object value) {
        this.wireSupport.updated(wire, value);
    }

    /** {@inheritDoc} */
    @Override
    public void onControlMessageArrived(String deviceId, String appTopic, KuraPayload msg, int qos, boolean retain) {
        // Not required
    }

    /** {@inheritDoc} */
    @Override
    public void onMessageArrived(String deviceId, String appTopic, KuraPayload msg, int qos, boolean retain) {
        // Not required
    }

    /** {@inheritDoc} */
    @Override
    public void onConnectionLost() {
        // Not required
    }

    // ----------------------------------------------------------------
    //
    // Private methods
    //
    // ----------------------------------------------------------------

    /**
     * Service tracker to manage Cloud Services
     */
    private void initCloudPublisherTracking() {
        String selectedCloudPublisherPid = this.cloudPublisherOptions.getCloudPublisherPid();
        String filterString = String.format("(&(%s=%s)(kura.service.pid=%s))", Constants.OBJECTCLASS,
                org.eclipse.kura.cloud.publisher.CloudPublisher.class.getName(), selectedCloudPublisherPid);
        Filter filter = null;
        try {
            filter = this.bundleContext.createFilter(filterString);
        } catch (InvalidSyntaxException e) {
            logger.error("Filter setup exception ", e);
        }
        this.cloudPublisherTracker = new ServiceTracker<>(this.bundleContext, filter,
                this.cloudPublisherTrackerCustomizer);
        this.cloudPublisherTracker.open();
    }

    /**
     * Builds the Kura payload from the provided {@link WireRecord}.
     *
     * @param wireRecord
     *            the {@link WireRecord}
     * @return the Kura payload
     * @throws NullPointerException
     *             if the {@link WireRecord} provided is null
     */
    private KuraPayload buildKuraPayload(final WireRecord wireRecord) {
        requireNonNull(wireRecord, "Wire Record cannot be null");
        final KuraPayload kuraPayload = new KuraPayload();

        kuraPayload.setTimestamp(new Date());

        if (this.cloudPublisherOptions.getPositionType() != PositionType.NONE) {
            KuraPosition kuraPosition = getPosition();
            kuraPayload.setPosition(kuraPosition);
        }

        for (final Entry<String, TypedValue<?>> entry : wireRecord.getProperties().entrySet()) {
            kuraPayload.addMetric(entry.getKey(), entry.getValue().getValue());
        }

        return kuraPayload;
    }

    private KuraPosition getPosition() {
        NmeaPosition position = this.positionService.getNmeaPosition();

        KuraPosition kuraPosition = new KuraPosition();
        kuraPosition.setAltitude(position.getAltitude());
        kuraPosition.setLatitude(position.getLatitude());
        kuraPosition.setLongitude(position.getLongitude());

        if (this.cloudPublisherOptions.getPositionType() == PositionType.FULL) {
            kuraPosition.setHeading(position.getTrack());
            kuraPosition.setPrecision(position.getDOP());
            kuraPosition.setSpeed(position.getSpeed());
            kuraPosition.setSatellites(position.getNrSatellites());
        }

        return kuraPosition;
    }

    /**
     * Publishes the list of provided {@link WireRecord}s
     *
     * @param wireRecords
     *            the provided list of {@link WireRecord}s
     * @throws NullPointerException
     *             if one of the arguments is null
     */
    private void publish(final List<WireRecord> wireRecords) {
        requireNonNull(wireRecords, "Wire Records cannot be null");

        try {
            for (final WireRecord dataRecord : wireRecords) {
                final KuraPayload kuraPayload = buildKuraPayload(dataRecord);
                this.cloudPublisher.publish(kuraPayload);
            }
        } catch (final Exception e) {
            logger.error("Error in publishing wire records using cloud publisher..", e);
        }
    }
}
