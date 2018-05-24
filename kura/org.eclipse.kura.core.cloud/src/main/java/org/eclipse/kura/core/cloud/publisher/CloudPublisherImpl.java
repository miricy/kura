package org.eclipse.kura.core.cloud.publisher;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.cloud.publisher.CloudPublisher;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.message.KuraPayload;


public class CloudPublisherImpl implements CloudPublisher, ConfigurableComponent {
    
    //Take cloud service with tracker

    @Override
    public int publish(KuraPayload message) throws KuraException {
        //Use the dataservice to publish
        // TODO Auto-generated method stub
        return 0;
    }

}
