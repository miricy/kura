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

import org.eclipse.kura.KuraErrorCode;
import org.eclipse.kura.KuraException;
import org.eclipse.kura.message.KuraPayload;

/**
 * @since 2.0
 */
public interface CloudletInterface {

    public default KuraPayload doGet(CloudletResources reqResources, KuraPayload reqPayload) throws KuraException {
        throw new KuraException(KuraErrorCode.OPERATION_NOT_SUPPORTED);
    }

    public default KuraPayload doPut(CloudletResources reqResources, KuraPayload reqPayload) throws KuraException {
        throw new KuraException(KuraErrorCode.OPERATION_NOT_SUPPORTED);
    }

    public default KuraPayload doPost(CloudletResources reqResources, KuraPayload reqPayload) throws KuraException {
        throw new KuraException(KuraErrorCode.OPERATION_NOT_SUPPORTED);
    }

    public default KuraPayload doDel(CloudletResources reqResources, KuraPayload reqPayload) throws KuraException {
        throw new KuraException(KuraErrorCode.OPERATION_NOT_SUPPORTED);
    }

    public default KuraPayload doExec(CloudletResources reqResources, KuraPayload reqPayload) throws KuraException {
        throw new KuraException(KuraErrorCode.OPERATION_NOT_SUPPORTED);
    }

}
