# Notifications

## Usage

Interesting parties register for a set of notification types related to a supplied identifier. The first step is to get a reference to the registrar `NotificationRegistrar registrar = NotificationRegistrar.getInstance();`. Next, one should determine the notification types they're interested in from the `NotificationType` enum:

* ANY
* NONE
* STATUS
* SUCCESS
* FAIL
* CREATED
* DISPOSED
* REGISTERED
* UNREGISTERED
* CONFIGURED
* INIT
* UNINIT
* START
* STOP
* CONNECTED
* DISCONNECTED
* PUBLISH_START
* PUBLISH_STOP
* RECORD_START
* RECORD_STOP
* SUBSCRIBE_START
* SUBSCRIBE_STOP
* PROGRESS
* IDLE
* USER_DEFINED
* UPLOAD
* AUDIO_SAMPLE
* VIDEO_KEYFRAME
* VIDEO_FRAME

The selected types are added to an `INotifiable` implementation which is registered with the generated identifier like so:

```java
String identifier = "my-guid-for-whatever";
registrar.register(identifier, new MediaNotifiable(EnumSet.of(NotificationType.SUCCESS, NotificationType.FAIL, NotificationType.DISPOSED)) {

    @Override
    public void onNotification(INotification notification) {
        NotificationType type = notification.getType();
        if (type == NotificationType.SUCCESS) {
            System.out.println("SUCCESS");
        } else if (type == NotificationType.FAIL) {
            System.err.println("FAIL");
        } else if (type == NotificationType.DISPOSED) {
            // unregister based on dispose notification
            registrar.unregister(notification.getIdentifier(), this);
        }
    }
    
});
```

Notifications are dispatched via the registrar, the identifier has to be passed twice, if its not in the actual notification its more difficult to sort or process the notification with their entity reference. The `MediaFileNotification` is an extension class that adds a map so that additional details can be passed around.

```java
registrar.dispatch(identifier, MediaFileNotification.build(this, NotificationType.PROGRESS, identifier, oldValue, newValue, details));
```

Dispatching `NotificationType.DISPOSED` when a process of notifications is completed for a given identifier is a good way to ensure things are cleaned up.

### Cloudstorage Plugin Example - FileUploader

Using the file uploader requires extra steps beyond writing your own post processor. The first step is determining the file path; this is the path to which the live stream was recorded, it is the source for post processing into other containers and uploading to the cloud. To get the file path, use the following code in your application:

```java
// get the notification registrar instance
final NotificationRegistrar registrar = NotificationRegistrar.getInstance();
// create a path for our stream using its name and scope
final String streamPath = stream.getScope().getContextPath() + '/' + stream.getName();
// create this notifiable for notification of success from the orientation post processor
final MediaNotifiable postProcNotifiable = new MediaNotifiable(EnumSet.of(NotificationType.SUCCESS, NotificationType.FAIL)) {

    @Override
    public void onNotification(INotification notification) {
        NotificationType type = notification.getType();
        switch (type) {
            case SUCCESS:
                // path to the file output of the post processor
                String outputFilePath = notification.getNewValue().toString();
                // do the upload or any other processing here
                doUpload(outputFilePath);
                break;
            case FAIL:
                // post proc failed, handle it as needed
                System.err.println("Post processor failed: " + notification.getNewValue().toString());
                break;
        }
        // unregister for notifications
        registrar.unregister(notification.getIdentifier(), this);
    }
    
};
// register for notifications on the "path" of our stream
registrar.register(streamPath, postProcNotifiable);
// create this notifiable for notifications on the ProStream (as well as disposed just in case of an issue)
final MediaNotifiable streamNotifiable = new MediaNotifiable(EnumSet.of(NotificationType.RECORD_START, NotificationType.RECORD_STOP, NotificationType.DISPOSED)) {

    @Override
    public void onNotification(INotification notification) {
        NotificationType type = notification.getType();
        switch (type) {
            case RECORD_START:
                // recording has started for our stream
                
                break;
            case RECORD_STOP:
                // when recording stops, post processing begins and we'll
                // need to wait for it to complete before doing our custom upload
                // get the recorded file's path so we can register for success
                registrar.register(notification.getNewValue().toString(), postProcNotifiable);
                // unregister for notifications on the stream
                registrar.unregister(streamPath, this);
                break;
            case DISPOSED:
                // recording didn't fire stop, so something bad happened, handle it as needed
                
                // unregister for notifications on the stream
                registrar.unregister(streamPath, this);
                break;        
        }
    }

};
// register for notifications on the "path" of our stream
registrar.register(streamPath, streamNotifiable);
```

**NOTE** When registering for notifications attached to a identifier or stream/file path in the example, if a set of notification types is not specified the `ANY` type will be the default. Lastly, ensure that when finished consuming notifications for an identifier, unregister for that identifier to prevent leaks.

A simple upload method example for the result of post processing.

```java
private void doUpload(String outputFilePath) {
    log.info("Upload: {}", outputFilePath);
    // an application or plugin may generate a url via some custom function and the file uploader
    // doesn't enforce any restrictions
    FileUploader uploader = new FileUploader();
    try {
        // all the uploader needs is the path to the file to be uploaded and the url destination
        uploader.upload(outputFilePath, uploadUrl);
    } catch (Exception e) {
        log.warn("Upload exception", e);
    }
}
```
