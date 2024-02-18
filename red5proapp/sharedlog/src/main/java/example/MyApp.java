package example;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.red5.logging.Red5LoggerFactory;
import org.red5.server.adapter.MultiThreadedApplicationAdapter;
import org.red5.server.api.IConnection;
import org.red5.server.api.IConnection.Encoding;
import org.red5.server.api.listeners.AbstractConnectionListener;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IBroadcastStream;
import org.red5.server.api.stream.IStreamListener;
import org.red5.server.api.stream.IStreamPacket;
import org.red5.server.api.stream.IStreamPlaybackSecurity;
import org.red5.server.api.stream.IStreamPublishSecurity;
import org.red5.server.api.stream.ISubscriberStream;
import org.red5.server.net.rtmp.event.AudioData;
import org.red5.server.net.rtmp.event.VideoData;
import org.slf4j.Logger;

import com.red5pro.override.IProStream;

/**
 * This example application adapter.
 *
 * @author Paul Gregoire
 * @author Andy Shaules
 */
public class MyApp extends MultiThreadedApplicationAdapter implements IStreamListener {

    // uses the plugins logger
    private static Logger log = Red5LoggerFactory.getLogger(MyApp.class, "myplugin");

    private static boolean isDebug = log.isDebugEnabled();

    private static ConcurrentHashMap<String, IBroadcastStream> liveStreams = new ConcurrentHashMap<>();

    @Override
    public boolean appStart(IScope scope) {
        log.info("appStart");
        // register publish security
        registerStreamPublishSecurity(new IStreamPublishSecurity() {

            @Override
            public boolean isPublishAllowed(IScope scope, String name, String mode) {
                log.info("isPublishAllowed {} {}", scope.getContextPath(), name);
                return true;
            }

        });        
        // register playback security
        registerStreamPlaybackSecurity(new IStreamPlaybackSecurity() {

            @Override
            public boolean isPlaybackAllowed(IScope scope, String name, int start, int length, boolean flushPlaylist) {
                log.info("isPlaybackAllowed {} {}", scope.getContextPath(), name);
                return true;
            }

        });
        return true;
    }

    @Override
    public void appStop(IScope scope) {
        log.info("appStop");
        super.appStop(scope);
    }

    @Override
    public boolean appConnect(IConnection conn, Object[] params) {
        log.info("appConnect");
        // debug for publish security at this stage
        log.debug("Security implementations - publish: {} playback: {}", getStreamPublishSecurity(), getStreamPlaybackSecurity());
        // show type of client encoding / protocol
        Encoding encoding = conn.getEncoding();
        log.debug("Connection encoding: {}", encoding);
        // add a connection listener for the property changes
        conn.addListener(new AbstractConnectionListener() {
            @Override
            public void notifyConnected(IConnection conn) {
                // this event has already fired by the time we add this listener, so we will never recieve it here..
            }

            @Override
            public void notifyDisconnected(IConnection conn) {
                // no-op
            }

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                log.debug("Connection propertyChange: {}", evt);
            }

        });
        return super.appConnect(conn, params);
    }

    @Override
    public void appDisconnect(IConnection conn) {
        log.info("appDisconnect");
        super.appDisconnect(conn);
    }

    @Override
    public void streamPublishStart(IBroadcastStream stream) {
        log.info("streamPublishStart: {}", stream);
        super.streamPublishStart(stream);
    }

    /**
     * Called when a client begins to publish media or data.
     */
    @Override
    public void streamBroadcastStart(IBroadcastStream stream) {
        log.info("streamBroadcastStart: {}", stream.getPublishedName());
        String key = String.format("%s@%s", stream.getPublishedName(), stream.getScope().getContextPath());
        // add to the live streams map
        liveStreams.put(key, stream);
        super.streamBroadcastStart(stream);
    }

    @Override
    public void streamBroadcastClose(IBroadcastStream stream) {
        log.info("streamBroadcastClose: {}", stream.getPublishedName());
        String key = String.format("%s@%s", stream.getPublishedName(), stream.getScope().getContextPath());
        // remove from the live streams map
        liveStreams.remove(key);
        super.streamBroadcastClose(stream);
    }

    @Override
    public void packetReceived(IBroadcastStream stream, IStreamPacket packet) {
        if (isDebug) {
            log.debug("packetReceived: {}", stream.getPublishedName());
            if (packet instanceof AudioData) {
                log.trace("Video received: {}", packet);
            } else if (packet instanceof VideoData) {
                log.trace("Video received: {}", packet);
            } else {
                log.trace("Other packet received: {}", packet);
            }
        }
    }

    @Override
    public void streamRecordStart(IBroadcastStream stream) {
        log.info("streamRecordStart: {}", stream);
        super.streamRecordStart(stream);
    }

    @Override
    public void streamRecordStop(IBroadcastStream stream) {
        log.info("streamRecordStop: {}", stream);
        super.streamRecordStop(stream);
    }

    @Override
    public void streamSubscriberStart(ISubscriberStream stream) {
        log.info("streamSubscriberStart: {}", stream.getBroadcastStreamPublishName());
        super.streamSubscriberStart(stream);
    }

    @Override
    public void streamSubscriberClose(ISubscriberStream stream) {
        log.info("streamSubscriberClose: {}", stream.getBroadcastStreamPublishName());
    }

    public IProStream getStream(String path, String name) {
        log.info("getStream - path: {} name: {}", path, name);
        String key = String.format("%s@%s", name, path);
        return (IProStream) liveStreams.get(key);
    }

    public IProStream getLiveStream(String streamName) {
        log.info("getLiveStream: {}", streamName);
        AtomicReference<IProStream> stream = new AtomicReference<>();
        liveStreams.forEach((key, value) -> {
            if (value.getPublishedName().equals(streamName)) {
                stream.compareAndSet(null, (IProStream) value);
            }
        });
        return stream.get();
    }

    public List<String> getLiveStreams() {
        log.info("getLiveStreams()");
        // create a list of available stream names
        final Set<String> streams = new HashSet<>();
        // run through the stream names for the scope
        getBroadcastStreamNames(scope).forEach(streamName -> {
            IProStream stream = getLiveStream(streamName);
            if (stream != null) {
                streams.add(stream.getPublishedName());
            }
        });
        log.info("Streams: {}", streams);
        return streams.stream().collect(Collectors.toCollection(ArrayList::new));
    }

}
