package opsmap.shared;

import java.io.Serializable;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    private final MessageType type;
    private final Object payload;
    private final String sender;

    public Message(MessageType type, Object payload, String sender) {
        this.type = type;
        this.payload = payload;
        this.sender = sender;
    }

    public MessageType getType() {
        return type;
    }

    public Object getPayload() {
        return payload;
    }

    public String getSender() {
        return sender;
    }
}
