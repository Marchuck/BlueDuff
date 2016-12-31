package pl.lukmarr.blueduff2;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

import io.reactivex.Observable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

/**
 * Project "BlueDuff"
 * <p>
 * Created by Lukasz Marczak
 * on 31.12.2016.
 */

public final class BlueDuffie {

    final int bufferCapacity;
    final long bluetoothSleep;
    final String charsetString;
    final boolean isSecure;
    final ConnectionCallbacks connectionCallbacks;

    BluetoothSocket bluetoothSocket;
    InputStreamWrapper inputStream;
    OutputStreamWrapper outputStream;

    public static class Builder {

        int bufferCapacity;
        long bluetoothSleep;
        String charsetString;
        boolean isSecureCommunication;
        final ConnectionCallbacks connectionCallbacks;

        public void setSecureCommunication(boolean secureCommunication) {
            isSecureCommunication = secureCommunication;
        }

        public Builder(ConnectionCallbacks connectionCallbacks) {
            this.connectionCallbacks = connectionCallbacks;
        }

        public Builder setBufferCapacity(int bufferCapacity) {
            this.bufferCapacity = bufferCapacity;
            return this;
        }

        public Builder setBluetoothSleep(long bluetoothSleep) {
            this.bluetoothSleep = bluetoothSleep;
            return this;
        }

        public Builder setCharsetString(String charsetString) {
            this.charsetString = charsetString;
            return this;
        }

        public BlueDuffie build() {
            return new BlueDuffie(bufferCapacity, bluetoothSleep, charsetString, connectionCallbacks, isSecureCommunication);
        }
    }

    private BlueDuffie(int bufferCapacity, long bluetoothSleep, String charsetString,
                       ConnectionCallbacks c, boolean isSecure) {
        this.bufferCapacity = bufferCapacity;
        this.bluetoothSleep = bluetoothSleep;
        this.charsetString = charsetString;
        this.connectionCallbacks = c;
        this.isSecure = isSecure;
    }


    public Set<BluetoothDevice> listDevices() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

        if (adapter.isEnabled()) return BluetoothAdapter.getDefaultAdapter().getBondedDevices();
        return Collections.emptySet();
    }

    public void connect(final BluetoothDevice device) {

        connectToDevice(device)
                .map(new Function<BluetoothSocket, Object>() {
                    @Override
                    public Object apply(BluetoothSocket _bluetoothSocket) throws Exception {
                        bluetoothSocket = _bluetoothSocket;
                        createInputStream();
                        createOutputStream();
                        return new Object();
                    }
                }).subscribeOn(Schedulers.computation())
                .subscribe(new Consumer<Object>() {
                    @Override
                    public void accept(Object no_op) throws Exception {

                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        connectionCallbacks.onError(throwable.getMessage());
                    }
                });

    }

    void createInputStream() {
        inputStream = new InputStreamWrapper(bluetoothSocket, charsetString, bufferCapacity, bluetoothSleep, connectionCallbacks);
    }

    void createOutputStream() {
        outputStream = new OutputStreamWrapper(bluetoothSocket, charsetString, connectionCallbacks);
    }

    Observable<BluetoothSocket> connectToDevice(final BluetoothDevice device) {
        return Observable.fromCallable(new Callable<BluetoothSocket>() {
            @Override
            public BluetoothSocket call() throws Exception {
                try {
                    UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
                    bluetoothSocket = isSecure ?
                            device.createRfcommSocketToServiceRecord(uuid) :
                            device.createInsecureRfcommSocketToServiceRecord(uuid);
                    bluetoothSocket.connect();
                    return bluetoothSocket;
                } catch (IOException e) {
                    throw new CannotConnectException(e.getMessage());
                }
            }
        });
    }

    public void disconnect() {
        try {
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
            if (bluetoothSocket != null) bluetoothSocket.close();
        } catch (IOException x) {
            connectionCallbacks.onDisconnected();
        }
    }

}
