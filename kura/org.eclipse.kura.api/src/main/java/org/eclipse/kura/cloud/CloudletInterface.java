/*******************************************************************************
 * Copyright (c) 2018 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
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
