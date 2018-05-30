package org.eclipse.kura.core.cloud;

import java.util.Date;
import java.util.concurrent.Callable;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.cloud.CloudletInterface;
import org.eclipse.kura.cloud.CloudletTopic;
import org.eclipse.kura.data.DataService;
import org.eclipse.kura.message.KuraPayload;
import org.eclipse.kura.message.KuraRequestPayload;
import org.eclipse.kura.message.KuraResponsePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageHandlerCallable implements Callable<Void> {

    private static final Logger logger = LoggerFactory.getLogger(MessageHandlerCallable.class);

    protected static final int DFLT_PUB_QOS = 0;
    protected static final boolean DFLT_RETAIN = false;
    protected static final int DFLT_PRIORITY = 1;

    private final CloudletInterface cloudApp;
    private final String appId;
    private final String appTopic;
    private final KuraPayload kuraMessage;
    private final CloudServiceImpl cloudService;

    public MessageHandlerCallable(CloudletInterface cloudApp, String appId, String appTopic,
            KuraPayload msg, CloudServiceImpl cloudService) {
        super();
        this.cloudApp = cloudApp;
        this.appId = appId;
        this.appTopic = appTopic;
        this.kuraMessage = msg;
        this.cloudService = cloudService;
    }

    @Override
    public Void call() throws Exception {
        logger.debug("Control Arrived on topic: {}", this.appTopic);

        // Prepare the default response
        KuraRequestPayload reqPayload = KuraRequestPayload.buildFromKuraPayload(this.kuraMessage);
        KuraResponsePayload respPayload = new KuraResponsePayload(KuraResponsePayload.RESPONSE_CODE_OK);

        try {
            CloudletTopic reqTopic = CloudletTopic.parseAppTopic(this.appTopic);
            CloudletTopic.Method method = reqTopic.getMethod();
            switch (method) {
            case GET:
                logger.debug("Handling GET request topic: {}", this.appTopic);
                this.cloudApp.doGet(reqTopic, reqPayload, respPayload);
                break;

            case PUT:
                logger.debug("Handling PUT request topic: {}", this.appTopic);
                this.cloudApp.doPut(reqTopic, reqPayload, respPayload);
                break;

            case POST:
                logger.debug("Handling POST request topic: {}", this.appTopic);
                this.cloudApp.doPost(reqTopic, reqPayload, respPayload);
                break;

            case DEL:
                logger.debug("Handling DEL request topic: {}", this.appTopic);
                this.cloudApp.doDel(reqTopic, reqPayload, respPayload);
                break;

            case EXEC:
                logger.debug("Handling EXEC request topic: {}", this.appTopic);
                this.cloudApp.doExec(reqTopic, reqPayload, respPayload);
                break;

            default:
                logger.error("Bad request topic: {}", this.appTopic);
                respPayload.setResponseCode(KuraResponsePayload.RESPONSE_CODE_BAD_REQUEST);
                break;
            }
        } catch (IllegalArgumentException e) {
            logger.error("Bad request topic: {}", this.appTopic);
            respPayload.setResponseCode(KuraResponsePayload.RESPONSE_CODE_BAD_REQUEST);
        } catch (KuraException e) {
            logger.error("Error handling request topic: {}\n{}", this.appTopic, e);
            respPayload.setResponseCode(KuraResponsePayload.RESPONSE_CODE_ERROR);
            respPayload.setException(e);
        }

        try {
            respPayload.setTimestamp(new Date());

            StringBuilder sb = new StringBuilder("REPLY").append("/").append(reqPayload.getRequestId());

            String requesterClientId = reqPayload.getRequesterClientId();

            logger.debug("Publishing response topic: {}", sb);
            boolean isControl = true;
            DataService dataService = this.cloudService.getDataService();
            String fullTopic = encodeTopic(requesterClientId, sb.toString(), isControl);
            byte[] appPayload = this.cloudService.encodePayload(respPayload);
            dataService.publish(fullTopic, appPayload, DFLT_PUB_QOS, DFLT_RETAIN, DFLT_PRIORITY);
        } catch (KuraException e) {
            logger.error("Error publishing response for topic: {}\n{}", this.appTopic, e);
        }

        return null;
    }

    private String encodeTopic(String deviceId, String appTopic, boolean isControl) {
        CloudServiceOptions options = this.cloudService.getCloudServiceOptions();
        StringBuilder sb = new StringBuilder();
        if (isControl) {
            sb.append(options.getTopicControlPrefix()).append(options.getTopicSeparator());
        }

        sb.append(options.getTopicAccountToken()).append(options.getTopicSeparator()).append(deviceId)
                .append(options.getTopicSeparator()).append(this.appId);

        if (appTopic != null && !appTopic.isEmpty()) {
            sb.append(options.getTopicSeparator()).append(appTopic);
        }

        return sb.toString();
    }
}
