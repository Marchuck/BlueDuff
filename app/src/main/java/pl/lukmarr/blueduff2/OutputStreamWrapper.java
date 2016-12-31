package pl.lukmarr.blueduff2;

import android.bluetooth.BluetoothSocket;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.Callable;

import io.reactivex.Observable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;

/**
 * Project "BlueDuff"
 * <p>
 * Created by Lukasz Marczak
 * on 31.12.2016.
 */

public class OutputStreamWrapper implements Closeable {

    OutputStream os;
    String charsetString;

    ConnectionCallbacks connectionCallbacks;

    public OutputStreamWrapper(final BluetoothSocket bluetoothSocket, String charsetString, ConnectionCallbacks connectionCallbacks) {
        this.charsetString = charsetString;
        this.connectionCallbacks = connectionCallbacks;

        openOutputStream(bluetoothSocket);
    }

    void openOutputStream(final BluetoothSocket bluetoothSocket) {

        Observable.fromCallable(new Callable<OutputStream>() {
            @Override
            public OutputStream call() throws Exception {
                return bluetoothSocket.getOutputStream();
            }
        }).map(new Function<OutputStream, Boolean>() {
            @Override
            public Boolean apply(OutputStream outputStream) throws Exception {
                os = outputStream;
                return true;
            }
        }).onErrorReturn(new Function<Throwable, Boolean>() {
            @Override
            public Boolean apply(Throwable throwable) throws Exception {
                return false;
            }
        }).subscribe(new Consumer<Boolean>() {
            @Override
            public void accept(Boolean canWrite) throws Exception {
                if (!canWrite) connectionCallbacks.onError("Cannot send data");
            }
        });
    }

    public void send(String message) {
        if (os != null) {

            byte[] data = stringToBytes(message);

            try {
                os.write(data);
            } catch (IOException e) {
                connectionCallbacks.onError(e.getMessage());
            }
        } else {
            connectionCallbacks.onError("Cannot send data");
        }
    }

    byte[] stringToBytes(String message) {
        try {
            return message.getBytes(charsetString);
        } catch (UnsupportedEncodingException e) {
            return message.getBytes();
        }
    }

    @Override
    public void close() throws IOException {
        if (os != null) {
            os.close();
        }
    }
}
