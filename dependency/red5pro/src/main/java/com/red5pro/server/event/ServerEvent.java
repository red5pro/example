package com.red5pro.server.event;

import java.beans.PropertyChangeEvent;

/**
 * Server event type identifier for filtering.
 *
 * @author Paul Gregoire
 *
 */
public enum ServerEvent {

    /**
     * The expectation is for use with future cross-plugin notification handling.
     */
    PLUGIN_START, // plugin started
    PLUGIN_STOP, // plugin stopped
    PLUGIN_PAUSE, // plugin paused (used with licensing)
    PLUGIN_RESUME, // plugin resumed (used with licensing)
    POST_PROCESSOR_START, // post processor has started
    POST_PROCESSOR_STOP; // post processor has stopped

    /**
     * Convenience method to build a server event without a "previous" value.
     *
     * @param type
     * @param source
     * @param newValue
     * @return PropertyChangeEvent
     */
    public static PropertyChangeEvent build(ServerEvent type, Object source, Object newValue) {
        return new PropertyChangeEvent(source, type.name(), null, newValue);
    }

    /**
     * Convenience method to build a server event.
     *
     * @param type
     * @param source
     * @param prevValue
     * @param newValue
     * @return PropertyChangeEvent
     */
    public static PropertyChangeEvent build(ServerEvent type, Object source, Object prevValue, Object newValue) {
        return new PropertyChangeEvent(source, type.name(), prevValue, newValue);
    }

}
