/*******************************************************************************
 * Copyright (c) 2019 Zhiju and/or its affiliates
 *
 *@author alva.huang
 *
 *******************************************************************************/
package cn.izhiju.kura.can;

import java.io.IOException;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.message.KuraPayload;
import org.eclipse.kura.protocol.can.CanConnectionService;
import org.eclipse.kura.protocol.can.CanMessage;
import org.eclipse.kura.cloudconnection.message.KuraMessage;
import org.eclipse.kura.cloudconnection.publisher.CloudPublisher;
import org.eclipse.kura.cloudconnection.subscriber.CloudSubscriber;
import org.eclipse.kura.cloudconnection.subscriber.listener.CloudSubscriberListener;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CanSocketGate implements ConfigurableComponent, CloudSubscriberListener {

    private static final Logger logger = LoggerFactory.getLogger(CanSocketGate.class);

    private static final String CAN_INTERFACE_NAME_PROP_NAME = "can.interface.name";
    private static final String CAN_IDENTIFIER_PROP_NAME = "can.identifier";
    private static final String CAN_IS_MASTER_PROP_NAME = "master";

    private static final String CAN_INTERFACE_DEFAULT = "can0";
    private static final Integer CAN_IDENTIFIER_DEFAULT = 128;
    private static final Boolean CAN_IS_MASTER_DEFAULT = true;

    private volatile CanConnectionService canConnection;
    private CloudPublisher cloudPublisher;
    private CloudPublisher cloudPublisherLan;
    private CloudSubscriber cloudSubscriber;
    private CloudSubscriber cloudSubscriberLan;

    private byte index = 0;
    private Thread worker;
    private String interfaceName = "can0";
    private int canId = 0;

    public void setCanConnectionService(CanConnectionService canConnection) {
        this.canConnection = canConnection;
    }

    public void unsetCanConnectionService(CanConnectionService canConnection) {
        this.canConnection = null;
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
        this.cloudSubscriber.registerCloudSubscriberListener(CanSocketGate.this);
    }

    public void unsetCloudSubscriber(CloudSubscriber cloudSubscriber) {
        this.cloudSubscriber.unregisterCloudSubscriberListener(CanSocketGate.this);
        this.cloudSubscriber = null;
    }
    
    public void setCloudSubscriberLan(CloudSubscriber cloudSubscriber) {
        this.cloudSubscriberLan = cloudSubscriber;
        this.cloudSubscriberLan.registerCloudSubscriberListener(CanSocketGate.this);
    }

    public void unsetCloudSubscriberLan(CloudSubscriber cloudSubscriber) {
        this.cloudSubscriberLan.unregisterCloudSubscriberListener(CanSocketGate.this);
        this.cloudSubscriberLan = null;
    }

    protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
        logger.info("activating...");

        try {
            this.canConnection.connectCanSocket();
        } catch (Exception e) {
            logger.error("failed to connect can socket", e);
            return;
        }

        updated(properties);

        logger.info("activating...done");
    }

    protected void deactivate(ComponentContext componentContext) {
        logger.info("deactivating...");
        cancelCurrentTask();

        try {
            this.canConnection.disconnectCanSocket();
        } catch (IOException e) {
            logger.error("failed to disconnect can socket", e);
        }
        logger.info("deactivating...done");
    }

    public void updated(Map<String, Object> properties) {
        logger.info("updating...");

        cancelCurrentTask();

		interfaceName = (String) properties.getOrDefault(CAN_INTERFACE_NAME_PROP_NAME,
				CAN_INTERFACE_DEFAULT);
		canId = (Integer) properties.getOrDefault(CAN_IDENTIFIER_PROP_NAME, CAN_IDENTIFIER_DEFAULT);
//		startSenderThread(interfaceName, canId, 1);

		startReceiverThread();


        logger.info("updating done...");
    }

    private void startSenderThread(String interfaceName, int canId, int dest) {
        this.worker = new Thread(() -> {
            while (!Thread.interrupted()) {
                int id = 0x500 + (canId << 4) + dest;
                StringBuilder sb = new StringBuilder("Try to send can frame with message = ");
                byte btest[] = new byte[]{0xf,0x01,0x01,0xf,0xf,0,0,0};
//                for (int i = 0; i < 8; i++) {
//                    btest[i] = (byte) (this.index + i);
//                    sb.append(btest[i]);
//                    sb.append(" ");
//                }
//                sb.append(" and id = ");
//                sb.append(id);
                logger.info(sb.toString());

                try {
                    this.canConnection.sendCanMessage(interfaceName, id, btest);
                } catch (Exception e) {
                    logger.warn("Failed to send CAN frame", e);
                }

                this.index++;
                if (this.index > 14) {
                    this.index = 0;
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "CanSenderThread");
        this.worker.start();
    }

    private void startReceiverThread() {
        this.worker = new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    logger.info("Wait for a request");
                    final CanMessage cm = this.canConnection.receiveCanMessage(-1, 0xFFFF);
                    logger.info("request received");
                    byte[] b = cm.getData();
                    if (b != null) {
                    	// Allocate a new payload
                        KuraPayload payload = new KuraPayload();

                        // Timestamp the message
                        payload.setTimestamp(new Date());
                        payload.setBody(b);

                        KuraMessage message = new KuraMessage(payload);
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
//                        sb.append(cm.getCanId());
                    } else {
                        logger.warn("receive=null");
                    }
                } catch (IOException e) {
                    logger.warn("CanConnection Crash : {}", e.getMessage());
                }
            }
        }, "CanReceiverThread");
        this.worker.start();
    }

    private void cancelCurrentTask() {
        if (this.worker != null) {
            this.worker.interrupt();
            try {
                this.worker.join(1000);
            } catch (InterruptedException e) {
            }
            this.worker = null;
        }
    }
    
    @Override
    public void onMessageArrived(KuraMessage message) {
        // TODO Auto-generated method stub
    	logger.info("For can ---onMessageArrived message---body: {}",  message.getPayload().getBody());

		try {
			byte[] sizeData = message.getPayload().getBody();//new byte[] {  0x05, 0x01, 0x01, 0x02, 0x02, 0x00, (byte) 0xfb };
			byte[] sendData = new byte[8];
			
			logger.info("For can ---onMessageArrived message my----body: {}", Base64.getEncoder().encodeToString(sizeData));
			if (sizeData != null) {
				if(sizeData.length>8) {
				System.arraycopy(sizeData, 3, sendData, 0, sizeData.length-3);
				this.canConnection.sendCanMessage(interfaceName, sizeData[4]+256, sendData);
				}
				else{
					this.canConnection.sendCanMessage(interfaceName, sizeData[4]+256, sizeData);
				}
			}
			logger.info("onMessageArrived can data: {}", sizeData);
		} catch (Exception e) {
			logger.error("Cannot read port", e);
		}
    }
}
