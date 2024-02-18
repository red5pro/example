package com.red5pro.server.event;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.EnumSet;

/**
 * Base class for entities interested in ServerEvent.
 *
 * @author Paul Gregoire
 *
 */
public class ServerEventListener implements PropertyChangeListener, Comparable<ServerEventListener> {

    // class that instantiated this instance
    private final Object owner;

    // set of event types to filter against to streamline notifications
    protected final EnumSet<ServerEvent> eventTypes;

    /**
     * Construct a listener for a set of event types.
     *
     * @param events
     */
    public ServerEventListener(Object owner, EnumSet<ServerEvent> events) {
        this.owner = owner;
        eventTypes = events;
    }

    /**
     * Whether or not this listener is interested in the event.
     *
     * @param eventType
     * @return true if interested and false otherwise
     */
    public boolean hasInterest(ServerEvent eventType) {
        return eventTypes.contains(eventType);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // implementation is left up to the implementer
    }

    public Object getOwner() {
        return owner;
    }

    public EnumSet<ServerEvent> getEventTypes() {
        return eventTypes;
    }

    @Override
    public int compareTo(ServerEventListener that) {
        int result = 0;
        Object thatOwner = that.getOwner();
        if (owner == null) {
            if (thatOwner != null) {
                // it has an owner and we dont so minus 1
                result -= 1;
            }
        } else {
            // we have an owner and theirs is not a match
            if (thatOwner != null && !owner.equals(thatOwner)) {
                result += 1;
            } else {
                // we have an owner and they dont so plus 1
                result += 1;
            }
        }
        EnumSet<ServerEvent> thatEvents = that.getEventTypes();
        if (eventTypes.size() > thatEvents.size()) {
            // we have more event types
            result += 1;
        } else if (eventTypes.size() < thatEvents.size()) {
            // they have more event types
            result -= 1;
        } else {
            // same number of events, so compare
            if (!eventTypes.containsAll(thatEvents)) {
                // we dont have the same event types
                result += 1;
            }
        }
        return result;
    }

}
