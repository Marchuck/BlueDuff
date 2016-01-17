package pl.lukmarr.blueduff;

/**
 * Created by Łukasz Marczak
 *
 * @since 17.01.16
 */
public class BlueInterfaces {

    public interface OnSocketKilledCallback {
        void onSocketKilled();
    }
    public interface DataReceivedCallback {
        void onDataReceived(byte[] packet);
    }
    public interface OnConnectedCallback {
        void onConnected();
    }
}
