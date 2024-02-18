package example;

import java.util.List;

public class LiveStreamListService {

    private final MyApp app;

    public LiveStreamListService(MyApp owner) {
        app = owner;
    }

    public List<String> getLiveStreams() {
        return app.getLiveStreams();
    }

}
