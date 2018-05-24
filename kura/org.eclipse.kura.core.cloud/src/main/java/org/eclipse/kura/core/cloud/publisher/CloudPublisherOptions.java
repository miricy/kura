package org.eclipse.kura.core.cloud.publisher;

import java.util.Map;

public class CloudPublisherOptions {

    private static final Property<String> CLOUD_SERVICE_PID = new Property<>("cloud.service.pid",
            "org.eclipse.kura.cloud.CloudService");
    private static final Property<String> APP_ID = new Property<>("appId", "heater");
    private static final Property<String> SEMANTIC_TOPIC = new Property<>("semantic.topic", "/data");
    private static final Property<Integer> QOS = new Property<>("qos", 0);
    private static final Property<Boolean> RETAIN = new Property<>("retain", false);
    private static final Property<String> MESSAGE_TYPE = new Property<>("message.type", "data");
    private static final Property<Integer> PRIORITY = new Property<>("priority", 7);

    private final String cloudServicePid;
    private final String appId;
    private final String semanticTopic;
    private final int qos;
    private final boolean retain;
    private final String messageType; // TODO: enum
    private final int priority;

    public CloudPublisherOptions(final Map<String, Object> properties) {
        this.cloudServicePid = CLOUD_SERVICE_PID.get(properties);
        this.appId = APP_ID.get(properties);
        this.semanticTopic = SEMANTIC_TOPIC.get(properties);
        this.qos = QOS.get(properties);
        this.retain = RETAIN.get(properties);
        this.messageType = MESSAGE_TYPE.get(properties);
        this.priority = PRIORITY.get(properties);
    }

    public String getCloudServicePid() {
        return this.cloudServicePid;
    }

    public String getAppId() {
        return this.appId;
    }
    
    public String getSemanticTopic() {
        return this.semanticTopic;
    }

    public int getQos() {
        return this.qos;
    }

    public boolean isRetain() {
        return this.retain;
    }

    public String getMessageType() {
        return this.messageType;
    }

    public int getPriority() {
        return this.priority;
    }

    private static final class Property<T> {

        private final String key;
        private final T defaultValue;

        public Property(final String key, final T defaultValue) {
            this.key = key;
            this.defaultValue = defaultValue;
        }

        @SuppressWarnings("unchecked")
        public T get(final Map<String, Object> properties) {
            final Object value = properties.get(this.key);

            if (this.defaultValue.getClass().isInstance(value)) {
                return (T) value;
            }
            return this.defaultValue;
        }
    }

}
