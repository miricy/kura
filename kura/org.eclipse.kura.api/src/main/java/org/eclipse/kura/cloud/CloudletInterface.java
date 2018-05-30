package org.eclipse.kura.cloud;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.message.KuraRequestPayload;
import org.eclipse.kura.message.KuraResponsePayload;

/**
 * @since 1.5
 */
public interface CloudletInterface {
    
    public default void doGet(CloudletTopic reqTopic, KuraRequestPayload reqPayload, KuraResponsePayload respPayload)
            throws KuraException {
        respPayload.setResponseCode(KuraResponsePayload.RESPONSE_CODE_NOTFOUND);
    }

    public default void doPut(CloudletTopic reqTopic, KuraRequestPayload reqPayload, KuraResponsePayload respPayload)
            throws KuraException {
        respPayload.setResponseCode(KuraResponsePayload.RESPONSE_CODE_NOTFOUND);
    }

    public default void doPost(CloudletTopic reqTopic, KuraRequestPayload reqPayload, KuraResponsePayload respPayload)
            throws KuraException {
        respPayload.setResponseCode(KuraResponsePayload.RESPONSE_CODE_NOTFOUND);
    }

    public default void doDel(CloudletTopic reqTopic, KuraRequestPayload reqPayload, KuraResponsePayload respPayload)
            throws KuraException {
        respPayload.setResponseCode(KuraResponsePayload.RESPONSE_CODE_NOTFOUND);
    }

    public default void doExec(CloudletTopic reqTopic, KuraRequestPayload reqPayload, KuraResponsePayload respPayload)
            throws KuraException {
        respPayload.setResponseCode(KuraResponsePayload.RESPONSE_CODE_NOTFOUND);
    }

}
