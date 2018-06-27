/*******************************************************************************
 * Copyright (c) 2011, 2016 Eurotech and/or its affiliates
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech
 *******************************************************************************/
package org.eclipse.kura.demo.heater;

import static java.util.Objects.nonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.eclipse.kura.cloud.CloudClient;
import org.eclipse.kura.cloud.CloudClientListener;
import org.eclipse.kura.cloud.CloudService;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.gpio.GPIOService;
import org.eclipse.kura.gpio.KuraClosedDeviceException;
import org.eclipse.kura.gpio.KuraGPIODeviceException;
import org.eclipse.kura.gpio.KuraGPIODirection;
import org.eclipse.kura.gpio.KuraGPIOMode;
import org.eclipse.kura.gpio.KuraGPIOPin;
import org.eclipse.kura.gpio.KuraGPIOTrigger;
import org.eclipse.kura.gpio.KuraUnavailableDeviceException;
import org.eclipse.kura.message.KuraPayload;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentException;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Heater implements ConfigurableComponent, CloudClientListener {

    /**
     * Inner class defined to track the CloudServices as they get added, modified or removed.
     * Specific methods can refresh the cloudService definition and setup again the Cloud Client.
     *
     */
    private final class GPIOServiceTrackerCustomizer implements ServiceTrackerCustomizer<GPIOService, GPIOService> {

        @Override
        public GPIOService addingService(final ServiceReference<GPIOService> reference) {
        	Heater.this.gpioService = Heater.this.bundleContext.getService(reference);
            return Heater.this.gpioService;
        }

        @Override
        public void modifiedService(final ServiceReference<GPIOService> reference, final GPIOService service) {
        	Heater.this.gpioService = Heater.this.bundleContext.getService(reference);
        }

        @Override
        public void removedService(final ServiceReference<GPIOService> reference, final GPIOService service) {
        	Heater.this.gpioService = null;
        }
    }
	
    private static final Logger s_logger = LoggerFactory.getLogger(Heater.class);

    // Cloud Application identifier
    private static final String APP_ID = "heater";

    // Publishing Property Names
    private static final String MODE_PROP_NAME = "mode";
    private static final String MODE_PROP_PROGRAM = "Program";
    private static final String MODE_PROP_MANUAL = "Manual";
    private static final String MODE_PROP_VACATION = "Vacation";

    private static final String PROGRAM_SETPOINT_NAME = "program.setPoint";
    private static final String MANUAL_SETPOINT_NAME = "manual.setPoint";

    private static final String TEMP_INITIAL_PROP_NAME = "temperature.initial";
    private static final String TEMP_INCREMENT_PROP_NAME = "temperature.increment";

    private static final String PUBLISH_RATE_PROP_NAME = "publish.rate";
    private static final String PUBLISH_TOPIC_PROP_NAME = "publish.semanticTopic";
    private static final String PUBLISH_QOS_PROP_NAME = "publish.qos";
    private static final String PUBLISH_RETAIN_PROP_NAME = "publish.retain";

    private CloudService m_cloudService;
    private CloudClient m_cloudClient;

    private final ScheduledExecutorService m_worker;
    private ScheduledFuture<?> m_handle;

    private float m_temperature;
    private Map<String, Object> m_properties;
    private final Random m_random;

    private GpioComponentOptions gpioComponentOptions;
    private ServiceTrackerCustomizer<GPIOService, GPIOService> gpioServiceTrackerCustomizer;
    private ServiceTracker<GPIOService, GPIOService> gpioServiceTracker;

    private BundleContext bundleContext;
    private GPIOService gpioService;

    private List<KuraGPIOPin> acquiredOutputPins = new ArrayList<>();
    private List<KuraGPIOPin> acquiredInputPins = new ArrayList<>();

    private ScheduledFuture<?> blinkTask = null;
    private ScheduledFuture<?> pollTask = null;

    private boolean value;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();


    // ----------------------------------------------------------------
    //
    // Dependencies
    //
    // ----------------------------------------------------------------

    public Heater() {
        super();
        this.m_random = new Random();
        this.m_worker = Executors.newSingleThreadScheduledExecutor();
    }

    public void setCloudService(CloudService cloudService) {
        this.m_cloudService = cloudService;
    }

    public void unsetCloudService(CloudService cloudService) {
        this.m_cloudService = null;
    }

    // ----------------------------------------------------------------
    //
    // Activation APIs
    //
    // ----------------------------------------------------------------

    protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
        s_logger.info("Activating Heater...");

        this.m_properties = properties;
        for (String s : properties.keySet()) {
            s_logger.info("Activate - " + s + ": " + properties.get(s));
        }

        this.bundleContext = componentContext.getBundleContext();

        this.gpioComponentOptions = new GpioComponentOptions(properties);

        this.gpioServiceTrackerCustomizer = new GPIOServiceTrackerCustomizer();
        initGPIOServiceTracking();

        doUpdate(properties);
        // get the mqtt client for this application
        try {

            // Acquire a Cloud Application Client for this Application
            s_logger.info("Getting CloudClient for {}...", APP_ID);
            this.m_cloudClient = this.m_cloudService.newCloudClient(APP_ID);
            this.m_cloudClient.addCloudClientListener(this);

            // Don't subscribe because these are handled by the default
            // subscriptions and we don't want to get messages twice
            doUpdate(false);
        } catch (Exception e) {
            s_logger.error("Error during component activation", e);
            throw new ComponentException(e);
        }
        s_logger.info("Activating Heater... Done.");
    }

    protected void deactivate(ComponentContext componentContext) {
        s_logger.debug("Deactivating Heater...");

        // shutting down the worker and cleaning up the properties
        this.m_worker.shutdown();

        // Releasing the CloudApplicationClient
        s_logger.info("Releasing CloudApplicationClient for {}...", APP_ID);
        this.m_cloudClient.release();
        
        stopTasks();
        releasePins();

        if (nonNull(this.gpioServiceTracker)) {
            this.gpioServiceTracker.close();
        }

        this.executor.shutdownNow();

        s_logger.debug("Deactivating Heater... Done.");
    }

    public void updated(Map<String, Object> properties) {
        s_logger.info("Updated Heater...");

        // store the properties received
        this.m_properties = properties;
        for (String s : properties.keySet()) {
            s_logger.info("Update - " + s + ": " + properties.get(s));
        }

        this.gpioComponentOptions = new GpioComponentOptions(properties);

        if (nonNull(this.gpioServiceTracker)) {
            this.gpioServiceTracker.close();
        }
        initGPIOServiceTracking();
        // try to kick off a new job
        doUpdate(true);
        doUpdate(properties);
        s_logger.info("Updated Heater... Done.");
    }

    // ----------------------------------------------------------------
    //
    // Cloud Application Callback Methods
    //
    // ----------------------------------------------------------------

    @Override
    public void onControlMessageArrived(String deviceId, String appTopic, KuraPayload msg, int qos, boolean retain) {
        // TODO Auto-generated method stub
    	String msgonoff=(String) msg.getMetric("status");
    	String gpioName = (String) msg.getMetric("gpioname");
    	s_logger.info("onControlMessageArrived... Done. status:"+msgonoff);
    	KuraGPIOPin kuraPin = this.gpioService.getPinByName(gpioName);
    	if(msgonoff.equalsIgnoreCase("ON"))
    	{
    		try {    			
				if(kuraPin!=null) {
					if(kuraPin.isOpen()) {
					kuraPin.setValue(true);
					}else {
					  kuraPin.open();
					  kuraPin.setValue(true);
					 
					}
						
					s_logger.info("______________________________"+kuraPin.getValue()+
							" getDirection:"+kuraPin.getDirection().name()+" getMode:" + kuraPin.getMode().name());
				}				
				
				
			} catch (KuraUnavailableDeviceException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (KuraClosedDeviceException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (KuraGPIODeviceException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
    	}
    	else if(msgonoff.equalsIgnoreCase("OFF")) {
    		try {    			
				if(kuraPin!=null) {
					if(kuraPin.isOpen()) {
					kuraPin.setValue(false);
					}else {
					  kuraPin.open();
					  kuraPin.setValue(false);
					}
						
					s_logger.info("______________________________"+kuraPin.getValue()+
							" getDirection:"+kuraPin.getDirection().name()+" getMode:" + kuraPin.getMode().name());
				}								
				
			} catch (KuraUnavailableDeviceException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (KuraClosedDeviceException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (KuraGPIODeviceException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
    	}

    }

    @Override
    public void onMessageArrived(String deviceId, String appTopic, KuraPayload msg, int qos, boolean retain) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onConnectionLost() {
        // TODO Auto-generated method stub

    }

    @Override
    public void onConnectionEstablished() {
        // TODO Auto-generated method stub

    }

    @Override
    public void onMessageConfirmed(int messageId, String appTopic) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onMessagePublished(int messageId, String appTopic) {
        // TODO Auto-generated method stub

    }

    // ----------------------------------------------------------------
    //
    // Private Methods
    //
    // ----------------------------------------------------------------

    /**
     * Called after a new set of properties has been configured on the service
     */
    private void doUpdate(boolean onUpdate) {
        // cancel a current worker handle if one if active
        if (this.m_handle != null) {
            this.m_handle.cancel(true);
        }

        if (!this.m_properties.containsKey(TEMP_INITIAL_PROP_NAME)
                || !this.m_properties.containsKey(PUBLISH_RATE_PROP_NAME)) {
            s_logger.info(
                    "Update Heater - Ignore as properties do not contain TEMP_INITIAL_PROP_NAME and PUBLISH_RATE_PROP_NAME.");
            return;
        }

        // reset the temperature to the initial value
        if (!onUpdate) {
            this.m_temperature = (Float) this.m_properties.get(TEMP_INITIAL_PROP_NAME);
        }

        // schedule a new worker based on the properties of the service
        int pubrate = (Integer) this.m_properties.get(PUBLISH_RATE_PROP_NAME);
        this.m_handle = this.m_worker.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                Thread.currentThread().setName(getClass().getSimpleName());
                doPublish();
            }
        }, 0, pubrate, TimeUnit.SECONDS);
    }
    
    /**
     * Called after a new set of properties has been configured on the service
     */
    private void doUpdate(Map<String, Object> properties) {
        stopTasks();
        releasePins();

        this.value = false;
        acquirePins();

        if (!acquiredOutputPins.isEmpty()) {
            submitBlinkTask(2000, acquiredOutputPins);
        }

        if (!acquiredInputPins.isEmpty()) {
            String inputReadMode = this.gpioComponentOptions.getInputReadMode();

            if (GpioComponentOptions.INPUT_READ_MODE_PIN_STATUS_LISTENER.equals(inputReadMode)) {
                attachPinListeners(acquiredInputPins);
            } else if (GpioComponentOptions.INPUT_READ_MODE_POLLING.equals(inputReadMode)) {
                submitPollTask(500, acquiredInputPins);
            }
        }

    }

    /**
     * Called at the configured rate to publish the next temperature measurement.
     */
    private void doPublish() {
        // fetch the publishing configuration from the publishing properties
        String topic = (String) this.m_properties.get(PUBLISH_TOPIC_PROP_NAME);
        Integer qos = (Integer) this.m_properties.get(PUBLISH_QOS_PROP_NAME);
        Boolean retain = (Boolean) this.m_properties.get(PUBLISH_RETAIN_PROP_NAME);
        String mode = (String) this.m_properties.get(MODE_PROP_NAME);

        // Increment the simulated temperature value
        float setPoint = 0;
        float tempIncr = (Float) this.m_properties.get(TEMP_INCREMENT_PROP_NAME);
        if (MODE_PROP_PROGRAM.equals(mode)) {
            setPoint = (Float) this.m_properties.get(PROGRAM_SETPOINT_NAME);
        } else if (MODE_PROP_MANUAL.equals(mode)) {
            setPoint = (Float) this.m_properties.get(MANUAL_SETPOINT_NAME);
        } else if (MODE_PROP_VACATION.equals(mode)) {
            setPoint = 6.0F;
        }
        if (this.m_temperature + tempIncr < setPoint) {
            this.m_temperature += tempIncr;
        } else {
            this.m_temperature -= 4 * tempIncr;
        }

        // Allocate a new payload
        KuraPayload payload = new KuraPayload();

        // Timestamp the message
        payload.setTimestamp(new Date());

        // Add the temperature as a metric to the payload
        payload.addMetric("temperatureInternal", this.m_temperature);
        payload.addMetric("temperatureExternal", 5.0F);
        payload.addMetric("temperatureExhaust", 30.0F);
        payload.addMetric("status", "ON");

        int code = this.m_random.nextInt();
        if (this.m_random.nextInt() % 5 == 0) {
            payload.addMetric("errorCode", code);
        } else {
            payload.addMetric("errorCode", 0);
        }

        // Publish the message
        try {
            this.m_cloudClient.publish(topic, payload, qos, retain);
            s_logger.info("Published to {} message: {}", topic, payload);
        } catch (Exception e) {
            s_logger.error("Cannot publish topic: " + topic, e);
        }
    }
    
    private void acquirePins() {
        if (this.gpioService != null) {
        	s_logger.info("______________________________");
        	s_logger.info("Available GPIOs on the system:");
            Map<Integer, String> gpios = this.gpioService.getAvailablePins();
            for (Entry<Integer, String> e : gpios.entrySet()) {
            	s_logger.info("#{} - [{}]", e.getKey(), e.getValue());
            }
            s_logger.info("______________________________");            
            getPins();
           
        }
    }

    private void getPins() {
        String[] pins = this.gpioComponentOptions.getPins();
        Integer[] directions = this.gpioComponentOptions.getDirections();
        Integer[] modes = this.gpioComponentOptions.getModes();
        Integer[] triggers = this.gpioComponentOptions.getTriggers();
        for (int i = 0; i < pins.length; i++) {
            try {
            	s_logger.info("Acquiring GPIO pin {} with params:", pins[i]);
            	s_logger.info("   Direction....: {}", directions[i]);
            	s_logger.info("   Mode.........: {}", modes[i]);
            	s_logger.info("   Trigger......: {}", triggers[i]);
                KuraGPIOPin p = getPin(pins[i], getPinDirection(directions[i]), getPinMode(modes[i]),
                        getPinTrigger(triggers[i]));
                if (p != null) {
                    p.open();
                    s_logger.info("GPIO pin {} acquired", pins[i]);
                    if (p.getDirection() == KuraGPIODirection.OUTPUT) {
                        acquiredOutputPins.add(p);
                    } else {
                        acquiredInputPins.add(p);
                    }
                } else {
                	s_logger.info("GPIO pin {} not found", pins[i]);
                }
            } catch (IOException e) {
            	s_logger.error("I/O Error occurred!", e);
            } catch (Exception e) {
            	s_logger.error("got errror", e);
            }
        }
    }

    private KuraGPIOPin getPin(String resource, KuraGPIODirection pinDirection, KuraGPIOMode pinMode,
            KuraGPIOTrigger pinTrigger) {
        KuraGPIOPin pin = null;
        try {
            int terminal = Integer.parseInt(resource);
            if (terminal > 0 && terminal < 1255) {
                pin = this.gpioService.getPinByTerminal(Integer.parseInt(resource), pinDirection, pinMode, pinTrigger);
            }
        } catch (NumberFormatException e) {
            pin = this.gpioService.getPinByName(resource, pinDirection, pinMode, pinTrigger);
        }
        return pin;
    }

    private void submitBlinkTask(long delayMs, final List<KuraGPIOPin> outputPins) {
        this.blinkTask = this.executor.scheduleWithFixedDelay(() -> {
            for (KuraGPIOPin outputPin : outputPins) {
                try {
                	s_logger.info("Setting GPIO pin {} to {}", outputPin, this.value);
                    outputPin.setValue(this.value);
                } catch (KuraUnavailableDeviceException | KuraClosedDeviceException | IOException e) {
                    logException(outputPin, e);
                }
            }
            this.value = !this.value;
        }, 0, delayMs, TimeUnit.MILLISECONDS);
    }

    private void submitPollTask(long delayMs, final List<KuraGPIOPin> inputPins) {
        this.pollTask = this.executor.scheduleWithFixedDelay(() -> {
            for (KuraGPIOPin inputPin : inputPins) {
                try {
                	s_logger.info("input pin {} value {}", inputPin, inputPin.getValue());
                } catch (KuraUnavailableDeviceException | KuraClosedDeviceException | IOException e) {
                    logException(inputPin, e);
                }
            }
        }, 0, delayMs, TimeUnit.MILLISECONDS);
    }

    private void attachPinListeners(final List<KuraGPIOPin> inputPins) {
        for (final KuraGPIOPin pin : inputPins) {
        	s_logger.info("Attaching Pin Listener to GPIO pin {}", pin);
            try {
                pin.addPinStatusListener(value -> s_logger.info("Pin status for GPIO pin {} changed to {}", pin, value));
            } catch (Exception e) {
                logException(pin, e);
            }
        }
    }

    private void logException(KuraGPIOPin pin, Exception e) {
        if (e instanceof KuraUnavailableDeviceException) {
        	s_logger.warn("GPIO pin {} is not available for export.", pin);
        } else if (e instanceof KuraClosedDeviceException) {
        	s_logger.warn("GPIO pin {} has been closed.", pin);
        } else {
        	s_logger.error("I/O Error occurred!", e);
        }
    }

    private void stopTasks() {
        if (this.blinkTask != null) {
            this.blinkTask.cancel(true);
        }
        if (this.pollTask != null) {
            this.pollTask.cancel(true);
        }
    }

    private void releasePins() {
        Stream.concat(acquiredInputPins.stream(), acquiredOutputPins.stream()).forEach(pin -> {
            try {
            	s_logger.warn("Closing GPIO pin {}", pin);
                pin.close();
            } catch (IOException e) {
            	s_logger.warn("Cannot close pin!");
            }
        });
        acquiredInputPins.clear();
        acquiredOutputPins.clear();
    }

    private KuraGPIODirection getPinDirection(int direction) {
        switch (direction) {
        case 0:
        case 2:
            return KuraGPIODirection.INPUT;
        case 1:
        case 3:
            return KuraGPIODirection.OUTPUT;
        default:
            return KuraGPIODirection.OUTPUT;
        }
    }

    private KuraGPIOMode getPinMode(int mode) {
        switch (mode) {
        case 2:
            return KuraGPIOMode.INPUT_PULL_DOWN;
        case 1:
            return KuraGPIOMode.INPUT_PULL_UP;
        case 8:
            return KuraGPIOMode.OUTPUT_OPEN_DRAIN;
        case 4:
            return KuraGPIOMode.OUTPUT_PUSH_PULL;
        default:
            return KuraGPIOMode.OUTPUT_OPEN_DRAIN;
        }
    }

    private KuraGPIOTrigger getPinTrigger(int trigger) {
        switch (trigger) {
        case 0:
            return KuraGPIOTrigger.NONE;
        case 2:
            return KuraGPIOTrigger.RAISING_EDGE;
        case 3:
            return KuraGPIOTrigger.BOTH_EDGES;
        case 1:
            return KuraGPIOTrigger.FALLING_EDGE;
        default:
            return KuraGPIOTrigger.NONE;
        }
    }

    private void initGPIOServiceTracking() {
        String selectedGPIOServicePid = this.gpioComponentOptions.getGpioServicePid();
        String filterString = String.format("(&(%s=%s)(kura.service.pid=%s))", Constants.OBJECTCLASS,
                GPIOService.class.getName(), selectedGPIOServicePid);
        Filter filter = null;
        try {
            filter = this.bundleContext.createFilter(filterString);
        } catch (InvalidSyntaxException e) {
            s_logger.error("Filter setup exception ", e);
        }
        this.gpioServiceTracker = new ServiceTracker<>(this.bundleContext, filter, this.gpioServiceTrackerCustomizer);
        this.gpioServiceTracker.open();
    }
}
