package pl.lukmarr.blueduff2;


import android.bluetooth.BluetoothSocket;
import android.util.Log;

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

    Subject<String> readStream = BehaviorSubject.create();

    byte[] buffer = new byte[]{}, currentBuffer = new byte[]{};
    boolean receivedData = false;
    long bluetoothSleep;
    String charsetString;
    ConnectionCallbacks connectionCallbacks;
    InputStream is;

    public InputStreamWrapper(BluetoothSocket bluetoothSocket, String charsetString,
                              int bufferCapacity,
                              long bluetoothSleep,
                              ConnectionCallbacks connectionCallbacks) {
        this.buffer = new byte[bufferCapacity];
        this.bluetoothSleep = bluetoothSleep;
        this.charsetString = charsetString;
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
                if (success) {

                }
            }
        });
    }

    void receiveMessages() {

        int bytesReceived;
        while (true) {
            try {
                Thread.sleep(bluetoothSleep);
                bytesReceived = is.available();
                manageNextPacket(bytesReceived);
            } catch (Exception x) {
                connectionCallbacks.onError(x.getMessage());
            }
        }
    }

    private void manageNextPacket(int bytesReceived) throws IOException {

        if (bytesReceived > 0) {

            bytesReceived = is.read(buffer);
            byte[] newBuffer = new byte[bytesReceived];
            System.arraycopy(buffer, 0, newBuffer, 0, bytesReceived);

            if (receivedData) {
                int aLen = currentBuffer.length;
                int bLen = newBuffer.length;
                byte[] c = new byte[aLen + bLen];
                System.arraycopy(currentBuffer, 0, c, 0, aLen);
                System.arraycopy(newBuffer, 0, c, aLen, bLen);
                currentBuffer = c;
            } else {
                currentBuffer = newBuffer;
            }
            receivedData = true;
        } else {
            if (receivedData) {
                readStream.onNext(byteToString(currentBuffer));
            }
            receivedData = false;
        }
    }

      String byteToString(byte[] currentBuffer) {
        try {
            return new String(currentBuffer, charsetString);
        } catch (UnsupportedEncodingException e) {
            return new String(currentBuffer);
        }
    }

    @Override
    public void close() throws IOException {
        if (is != null) is.close();
    }
}
