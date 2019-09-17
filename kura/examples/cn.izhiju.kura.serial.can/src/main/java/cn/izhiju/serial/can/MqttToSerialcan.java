/*******************************************************************************
 * Copyright (c) 2019 Zhiju and/or its affiliates
 *
 *@author alva.huang
 *
 *******************************************************************************/
package cn.izhiju.serial.can;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.kura.cloudconnection.message.KuraMessage;
import org.eclipse.kura.cloudconnection.publisher.CloudPublisher;
import org.eclipse.kura.cloudconnection.subscriber.CloudSubscriber;
import org.eclipse.kura.cloudconnection.subscriber.listener.CloudSubscriberListener;
import org.eclipse.kura.comm.CommConnection;
import org.eclipse.kura.comm.CommURI;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.message.KuraPayload;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentException;
import org.osgi.service.io.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MqttToSerialcan implements ConfigurableComponent, CloudSubscriberListener {

    private static final Logger logger = LoggerFactory.getLogger(MqttToSerialcan.class);

    private static final String SERIAL_DEVICE_PROP_NAME = "serial.device";
    private static final String SERIAL_BAUDRATE_PROP_NAME = "serial.baudrate";
    private static final String SERIAL_DATA_BITS_PROP_NAME = "serial.data-bits";
    private static final String SERIAL_PARITY_PROP_NAME = "serial.parity";
    private static final String SERIAL_STOP_BITS_PROP_NAME = "serial.stop-bits";
    private static final String SERIAL_GATEWAY_ADDR = "serial.gateway-addr";

    private static final String SERIAL_ECHO_PROP_NAME = "serial.echo";

    private ConnectionFactory connectionFactory;

    private CommConnection commConnection;
    private InputStream commIs;
    private OutputStream commOs;

    private final ScheduledExecutorService worker;
    private Future<?> handle;

    private Map<String, Object> properties;

    private CloudPublisher cloudPublisher;
    private CloudPublisher cloudPublisherLan;
    private CloudSubscriber cloudSubscriber;
    private CloudSubscriber cloudSubscriberLan;

    // ----------------------------------------------------------------
    //
    // Dependencies
    //
    // ----------------------------------------------------------------

    public MqttToSerialcan() {
        super();
        this.worker = Executors.newSingleThreadScheduledExecutor();
    }

    public void setCloudPublisher(CloudPublisher cloudPublisher) {
        this.cloudPublisher = cloudPublisher;
    }

    public void unsetCloudPublisher(CloudPublisher cloudPublisher) {
        this.cloudPublisher = null;
    }
    
    public void setCloudPublisherLan(CloudPublisher cloudPublisher) {
        this.cloudPublisherLan = cloudPublisher;
    }

    public void unsetCloudPublisherLan(CloudPublisher cloudPublisher) {
        this.cloudPublisherLan = null;
    }
    
    public void setCloudSubscriber(CloudSubscriber cloudSubscriber) {
        this.cloudSubscriber = cloudSubscriber;
        this.cloudSubscriber.registerCloudSubscriberListener(MqttToSerialcan.this);
    }

    public void unsetCloudSubscriber(CloudSubscriber cloudSubscriber) {
        this.cloudSubscriber.unregisterCloudSubscriberListener(MqttToSerialcan.this);
        this.cloudSubscriber = null;
    }
    
    public void setCloudSubscriberLan(CloudSubscriber cloudSubscriber) {
        this.cloudSubscriberLan = cloudSubscriber;
        this.cloudSubscriberLan.registerCloudSubscriberListener(MqttToSerialcan.this);
    }

    public void unsetCloudSubscriberLan(CloudSubscriber cloudSubscriber) {
        this.cloudSubscriberLan.unregisterCloudSubscriberListener(MqttToSerialcan.this);
        this.cloudSubscriberLan = null;
    }

    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public void unsetConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = null;
    }

    // ----------------------------------------------------------------
    //
    // Activation APIs
    //
    // ----------------------------------------------------------------

    protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
        logger.info("Activating ExampleSerialPublisher...");

        this.properties = new HashMap<String, Object>();

        // get the mqtt client for this application
        try {

            // Don't subscribe because these are handled by the default
            // subscriptions and we don't want to get messages twice
            doUpdate(properties);
        } catch (Exception e) {
            logger.error("Error during component activation", e);
            throw new ComponentException(e);
        }
        logger.info("Activating ExampleSerialPublisher... Done.");
    }

    protected void deactivate(ComponentContext componentContext) {
        logger.info("Deactivating ExampleSerialPublisher...");

        this.handle.cancel(true);

        // shutting down the worker and cleaning up the properties
        this.worker.shutdownNow();

        closePort();

        logger.info("Deactivating ExampleSerialPublisher... Done.");
    }

    public void updated(Map<String, Object> properties) {
        logger.info("Updated ExampleSerialPublisher...");

        // try to kick off a new job
        doUpdate(properties);
        logger.info("Updated ExampleSerialPublisher... Done.");
    }

    // ----------------------------------------------------------------
    //
    // Private Methods
    //
    // ----------------------------------------------------------------

    /**
     * Called after a new set of properties has been configured on the service
     */
    private void doUpdate(Map<String, Object> properties) {
        try {

            for (String s : properties.keySet()) {
                logger.info("Update - " + s + ": " + properties.get(s));
            }

            // cancel a current worker handle if one if active
            if (this.handle != null) {
                this.handle.cancel(true);
            }

            closePort();

            this.properties.clear();
            this.properties.putAll(properties);

            openPort();

            this.handle = this.worker.submit(new Runnable() {

                @Override
                public void run() {
                    doSerial();
                }
            });
        } catch (Throwable t) {
            logger.error("Unexpected Throwable", t);
        }
    }

    private void openPort() {
        String port = (String) this.properties.get(SERIAL_DEVICE_PROP_NAME);

        if (port == null) {
            logger.info("Port name not configured");
            return;
        }

        int baudRate = Integer.valueOf((String) this.properties.get(SERIAL_BAUDRATE_PROP_NAME));
        int dataBits = Integer.valueOf((String) this.properties.get(SERIAL_DATA_BITS_PROP_NAME));
        int stopBits = Integer.valueOf((String) this.properties.get(SERIAL_STOP_BITS_PROP_NAME));

        String sParity = (String) this.properties.get(SERIAL_PARITY_PROP_NAME);

        int parity = CommURI.PARITY_NONE;
        if (sParity.equals("none")) {
            parity = CommURI.PARITY_NONE;
        } else if (sParity.equals("odd")) {
            parity = CommURI.PARITY_ODD;
        } else if (sParity.equals("even")) {
            parity = CommURI.PARITY_EVEN;
        }

        String uri = new CommURI.Builder(port).withBaudRate(baudRate).withDataBits(dataBits).withStopBits(stopBits)
                .withParity(parity).withTimeout(1000).build().toString();

        try {
            this.commConnection = (CommConnection) this.connectionFactory.createConnection(uri, 1, false);
            this.commIs = this.commConnection.openInputStream();
            this.commOs = this.commConnection.openOutputStream();

            logger.info("{} open", port);
        } catch (IOException e) {
            logger.error("Failed to open port", e);
            cleanupPort();
        }
    }

    private void cleanupPort() {
        if (this.commIs != null) {
            try {
                logger.info("Closing port input stream...");
                this.commIs.close();
                logger.info("Closed port input stream");
            } catch (IOException e) {
                logger.error("Cannot close port input stream", e);
            }
            this.commIs = null;
        }
        if (this.commOs != null) {
            try {
                logger.info("Closing port output stream...");
                this.commOs.close();
                logger.info("Closed port output stream");
            } catch (IOException e) {
                logger.error("Cannot close port output stream", e);
            }
            this.commOs = null;
        }
        if (this.commConnection != null) {
            try {
                logger.info("Closing port...");
                this.commConnection.close();
                logger.info("Closed port");
            } catch (IOException e) {
                logger.error("Cannot close port", e);
            }
            this.commConnection = null;
        }
    }

    private void closePort() {
        cleanupPort();
    }

    private void doSerial() {
        Boolean echo = (Boolean) this.properties.get(SERIAL_ECHO_PROP_NAME);
        String gatewayAddr = (String) this.properties.get(SERIAL_GATEWAY_ADDR);
        
        if (this.commIs != null) {

            try {
                int c = -1;
                byte[] body=null;
//                StringBuilder sb = new StringBuilder();
                logger.info("------------doSerial0");                
                while (this.commIs != null) {
                    int alen = this.commIs.available();
                    if (alen > 0) {
                    	body = new byte[alen];
                        c = this.commIs.read(body);
                    } else {
                        try {
                        	c=-1;
                            Thread.sleep(50);
                            continue;
                        } catch (InterruptedException e) {
                            return;
                        }
                    }

//                    if (echo && this.commOs != null) {
//                        this.commOs.write((char) c);
//                    }
                    logger.info("------------doSerial1: {}",c);
                    // on reception of CR, publish the received sentence
                    if (c >= 9) {
                    	c=-1;
                        // Allocate a new payload
                        KuraPayload payload = new KuraPayload();

                        // Timestamp the message
                        payload.setTimestamp(new Date());
                        payload.setBody(body);
                        Map<String,Object> hmap = new HashMap<String,Object>();
                        if(body[0]==0x59 && body[1]== 0x4B && ((body[5]&0x80)==0x80)) {// yeker protocal   
                        	body[5]=body[5]&0x7f;
                        	hmap.put("address", Integer.toHexString(0x00ff&body[8]));
                        }
                        else {
                        	hmap.put("address", "broadcast");
                        }
                        KuraMessage message = new KuraMessage(payload,hmap);

                        // Publish the message
                        try {
                            if(this.cloudPublisherLan!=null ) {
                            	this.cloudPublisherLan.publish(message);
                            	logger.info("Published message by lan");
                            }
                        	if(this.cloudPublisher!=null) {
                               this.cloudPublisher.publish(message);
                               logger.info("Published message: {}", payload);
                        	}
                        } catch (Exception e) {
                            logger.error("Cannot publish message: {}",message,  e);
                        }

                    } 
                }
            } catch (IOException e) {
                logger.error("Cannot read port", e);
            } finally {
                try {
                    if (this.commIs != null) {
                    this.commIs.close();
                    }
                } catch (IOException e) {
                    logger.error("Cannot close buffered reader", e);
                }
            }
        }
    }

    @Override
    public void onMessageArrived(KuraMessage message) {
    	String appTopic = (String) message.getProperties().get("appTopic");
    	String gatewayAddr = (String) this.properties.get(SERIAL_GATEWAY_ADDR);
    	logger.info("onMessageArrived message apptopic {} ---gatewayAddr: {}",appTopic,  gatewayAddr);
    	if(appTopic.equalsIgnoreCase(gatewayAddr)||appTopic.equalsIgnoreCase("data")) {
    	if (this.commOs != null) {
    		 try {
    			 if(message.getPayload().getBody() != null) {
    				 this.commOs.write(message.getPayload().getBody());
    				 logger.info("onMessageArrived serial data: {}",message.getPayload().getBody());
    			 }
    			 
    		 } catch (IOException e) {
                 logger.error("Cannot read port", e);
             } 
    	}
       }
    }
}
