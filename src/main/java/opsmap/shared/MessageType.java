package opsmap.shared;

import java.io.Serializable;

public enum MessageType implements Serializable {
    AUTH_REQUEST,
    AUTH_RESPONSE,
    // Add a drawing that should be visible to everyone.
    DRAWING_ADD,
    // Remove a drawing that belongs to the user.
    DRAWING_REMOVE,
    // Real-time pointer position updates.
    POINTER_UPDATE,
    // Chat messages for the room.
    CHAT_MESSAGE,
    // Full room state for late joiners.
    STATE_SYNC,
    // Active user list updates.
    USER_LIST,
    ERROR
}