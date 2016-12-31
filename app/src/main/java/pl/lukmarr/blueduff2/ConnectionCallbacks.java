package pl.lukmarr.blueduff2;

/**
 * Project "BlueDuff"
 * <p>
 * Created by Lukasz Marczak
 * on 31.12.2016.
 */

public interface ConnectionCallbacks {

    void onConnected();

    void onDisconnected();

    void onError(String errorMessage);
}
