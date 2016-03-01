package pl.lukmarr.blueduff;

/**
 * Created by Łukasz Marczak
 * <p/>
 * All possible callback which user can implement.
 * Every implementation of these anonymous classes
 * are not running in the main thread.
 *
 * @since 17.01.16
 */
public class BlueInterfaces {

    //avoid instantation
    private BlueInterfaces() {
    }

    /**
     * First callback when connection with device is established.
     */
    public interface OnConnectedCallback {
        void onConnected();
    }

    /**
     * Callback is fired every time phone is receiving data from device.
     */
    public interface DataReceivedCallback {
        void onDataReceived(byte[] packet);
    }

    /**
     * callback which informs that connection is finished,
     * after that we can connect to another device
     * {@link BlueDuff#closeStreams(OnSocketKilledCallback)}
     */
    public interface OnSocketKilledCallback {
        void onSocketKilled();
    }

    /**
     * Detect errors and show user suitable message.
     */
    public interface OnConnectionError {
        void onError(Throwable throwable);
    }

}
