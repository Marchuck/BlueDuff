package pl.lukmarr.blueduff2;


import android.bluetooth.BluetoothSocket;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.Callable;

import io.reactivex.Observable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.Subject;

/**
 * Project "BlueDuff"
 * <p>
 * Created by Lukasz Marczak
 * on 31.12.2016.
 */

public class InputStreamWrapper implements Closeable {

    boolean receivedData = false;
    byte[] currentBuffer = new byte[]{};
    InputStream is;
    ConnectionCallbacks connectionCallbacks;
    final BluetoothBundle bluetoothBundle;
    Subject<String> readStream = BehaviorSubject.create();

    public InputStreamWrapper(BluetoothSocket bluetoothSocket, BluetoothBundle bluetoothBundle,
                              ConnectionCallbacks connectionCallbacks) {
        this.bluetoothBundle = bluetoothBundle;
        this.connectionCallbacks = connectionCallbacks;

        openInputStream(bluetoothSocket);
    }

    Observable<InputStream> getInputStream(final BluetoothSocket socket) {
        return Observable.fromCallable(new Callable<InputStream>() {
            @Override
            public InputStream call() throws Exception {
                return socket.getInputStream();
            }
        });
    }

    private void openInputStream(BluetoothSocket bluetoothSocket) {

        getInputStream(bluetoothSocket).map(new Function<InputStream, Boolean>() {
            @Override
            public Boolean apply(InputStream inputStream) throws Exception {
                is = inputStream;
                receiveMessages();
                return true;
            }
        }).onErrorReturn(new Function<Throwable, Boolean>() {
            @Override
            public Boolean apply(Throwable throwable) throws Exception {
                return false;
            }
        }).subscribe(new Consumer<Boolean>() {
            @Override
            public void accept(Boolean success) throws Exception {
                if (!success) {
                    connectionCallbacks.onDisconnected();
                }
            }
        });
    }

    void receiveMessages() {

        int bytesReceived;

        byte[] buffer = new byte[bluetoothBundle.bufferCapacity];

        while (true) {
            try {
                Thread.sleep(bluetoothBundle.bluetoothSleep);

                bytesReceived = is.available();

                manageNextPacket(bytesReceived, buffer);

            } catch (Exception x) {
                connectionCallbacks.onError(x.getMessage());
            }
        }
    }

    private void manageNextPacket(int bytesReceived, byte[] buffer) throws IOException {

        if (bytesReceived > 0) {
            onBytesReceived(buffer);
        } else {
            onBytesRead();
        }
    }

    void onBytesRead() {

        if (receivedData) {
            readStream.onNext(bytesToString(currentBuffer));
        }

        receivedData = false;
    }

    void onBytesReceived(byte[] buffer) throws IOException {

        int bytesReceived = is.read(buffer);
        byte[] newBuffer = new byte[bytesReceived];
        System.arraycopy(buffer, 0, newBuffer, 0, bytesReceived);

        if (receivedData) {

            currentBuffer = onReceiveData(currentBuffer, newBuffer);

        } else {
            currentBuffer = newBuffer;
        }

        receivedData = true;
    }

    byte[] onReceiveData(byte[] currentBuffer, byte[] newBuffer) {
        int aLen = currentBuffer.length;
        int bLen = newBuffer.length;
        byte[] c = new byte[aLen + bLen];
        System.arraycopy(currentBuffer, 0, c, 0, aLen);
        System.arraycopy(newBuffer, 0, c, aLen, bLen);
        return c;
    }


    String bytesToString(byte[] currentBuffer) {
        try {
            return new String(currentBuffer, bluetoothBundle.charset);
        } catch (UnsupportedEncodingException e) {
            return new String(currentBuffer);
        }
    }

    @Override
    public void close() throws IOException {
        if (is != null) is.close();
    }
}
