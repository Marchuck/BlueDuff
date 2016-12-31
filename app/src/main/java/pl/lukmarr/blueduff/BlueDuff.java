package pl.lukmarr.blueduff;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresPermission;
import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import static android.Manifest.permission.BLUETOOTH;
import static android.Manifest.permission.BLUETOOTH_ADMIN;
import static android.bluetooth.BluetoothAdapter.getDefaultAdapter;

/**
 * @author Lukasz Marczak
 * @since 17.01.16
 * <p/>
 * Client - side communication with Bluetooth devices (e.g. micro-controllers via HC05 modules)
 * Class enables you connecting with device and pass data.
 * RxAndroid supports error handling
 *
 *
 * GOOD BYE RXJAVA 1.0!
 */
public class BlueDuff {
    public InputStream inputStream;
    public OutputStream outputStream;
    public static final String TAG = BlueDuff.class.getSimpleName();
    private final int BUFFER_CAPACITY;
    private final long COLLECT_PAYLOAD_DELAY;
    private byte[] currentBuffer;
    public BluetoothSocket bluetoothSocket;

    private LogLevel logLevel = LogLevel.VERBOSE;
    boolean receivedData = false;

    /**
     * @param logLevel VERBOSE, NONE or ERROR
     */
    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    /**
     * note: if implemetation exists,
     * this inputStream not running in main thread,
     */
    @Nullable
    private BlueInterfaces.OnConnectionError onConnectionErrorCallback;

    public void setOnConnectionErrorCallback(@Nullable BlueInterfaces.OnConnectionError onConnectionErrorCallback) {
        this.onConnectionErrorCallback = onConnectionErrorCallback;
    }

    public BlueDuff() {
        this(1024, 10);
    }

    public BlueDuff(int bufferCapacity, int collectPacketDelay) {
        if (bufferCapacity < 1)
            throw new RuntimeException("Buffer capacity " + bufferCapacity + " inputStream illegal value");
        if (collectPacketDelay < 0)
            throw new RuntimeException("Collect Packet Delay " + collectPacketDelay + " inputStream illegal value.");
        BUFFER_CAPACITY = bufferCapacity;
        COLLECT_PAYLOAD_DELAY = collectPacketDelay;
    }

    void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Kills previous streams and socket to avoid leaking resources
     *
     * @param listener - do something after closing living streams
     */
    public rx.Subscription closeStreams(@Nullable final BlueInterfaces.OnSocketKilledCallback listener) {
        return rx.Observable.create(new Observable.OnSubscribe<Boolean>() {
            @Override
            public void call(Subscriber<? super Boolean> subscriber) {
                close(inputStream);
                inputStream = null;
                close(outputStream);
                outputStream = null;
                close(bluetoothSocket);
                bluetoothSocket = null;

                subscriber.onNext(true);
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Boolean>() {
                    @Override
                    public void call(Boolean aBoolean) {
                        if (listener != null) listener.onSocketKilled();
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        if (logLevel != LogLevel.NONE) {
                            Log.e(TAG, throwable.getMessage());
                            throwable.printStackTrace();
                        }
                    }
                });
    }


    int write(byte[] bytes) {

        if (isVerbose()) Log.d(TAG, "write: " + bytes.length + " bytes");

        if (outputStream != null) {
            try {
                outputStream.write(bytes);
            } catch (Exception e) {
                return 0;
            }
            return bytes.length;
        } else {
            if (isVerbose()) Log.e(TAG, "Output stream not opened. Create connection first!");
            return 0;
        }
    }

    boolean isVerbose(){
        return logLevel == LogLevel.VERBOSE;
    }

    /**
     * @param bytes data to send
     * @return true, if output stream inputStream opened
     */
    public boolean writeData(byte[] bytes) {
        if (isVerbose())
            Log.d(TAG, "writeData here");
        boolean canWrite = outputStream != null;
        if (canWrite) Observable.just(write(bytes))
                .subscribeOn(Schedulers.io())
                .subscribe(new Action1<Integer>() {
                    @Override
                    public void call(Integer integer) {
                        if (isVerbose())
                            Log.d(TAG, integer + " bytes written!!!");
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        if (logLevel != LogLevel.NONE) {
                            Log.e(TAG, "onError: " + throwable.getMessage());
                            throwable.printStackTrace();
                        }
                        if (onConnectionErrorCallback != null)
                            onConnectionErrorCallback.onError(throwable);
                    }
                });
        return canWrite;
    }

    /**
     * @param socket               BluetoothSocket passed via rx chain
     * @param firstWriter          callback where you can do something, after connection inputStream ready
     * @param dataReceivedCallback handle data from input stream
     * @return hot observable for rx-chain
     */
    public Observable<Boolean> getStreamsWork(final BluetoothSocket socket,
                                              @Nullable final BlueInterfaces.OnConnectedCallback firstWriter,
                                              @NonNull final BlueInterfaces.DataReceivedCallback dataReceivedCallback) {
        return Observable.create(new Observable.OnSubscribe<Boolean>() {


            @Override
            public void call(Subscriber<? super Boolean> subscriber) {
                try {
                    inputStream = socket.getInputStream();
                    outputStream = socket.getOutputStream();
                } catch (Exception f) {
                    subscriber.onError(new Throwable("error on opening input stream"));
                } finally {
                    receivedData = false;
                    loopReceiveAndWrite(firstWriter, dataReceivedCallback, subscriber);
                }
            }
        });
    }

    void loopReceiveAndWrite(@Nullable final BlueInterfaces.OnConnectedCallback firstWriter,
                             @NonNull final BlueInterfaces.DataReceivedCallback dataReceivedCallback,
                             Subscriber<? super Boolean> subscriber) {

        boolean communicationNotCrashed = true;

        if (inputStream != null && outputStream != null) {
            if (firstWriter != null) firstWriter.onConnected();
            byte[] buffer = new byte[BUFFER_CAPACITY];
            int bytesReceived;

            while (communicationNotCrashed) {
                try {
                    Thread.sleep(COLLECT_PAYLOAD_DELAY);

                    bytesReceived = inputStream.available();

                    receiveBytes(bytesReceived, buffer, dataReceivedCallback, subscriber);
                } catch (Exception x) {
                    communicationNotCrashed = false;
                    subscriber.onError(new Throwable("error during reading input stream"));
                }
            }
            subscriber.onCompleted();
        } else subscriber.onError(new Throwable("i/o crashed"));
    }

    private void receiveBytes(int bytesReceived, byte[] buffer,
                              @NonNull final BlueInterfaces.DataReceivedCallback dataReceivedCallback,
                              Subscriber<? super Boolean> subscriber) throws Exception {
        if (bytesReceived > 0) {

            bytesReceived = inputStream.read(buffer);
            byte[] newBuffer = new byte[bytesReceived];
            System.arraycopy(buffer, 0, newBuffer, 0, bytesReceived);
            if (isVerbose())
                Log.d(TAG, "bytes count : " + bytesReceived);
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
                if (isVerbose())
                    Log.d(TAG, "received data: " + currentBuffer.length + " bytes");
                dataReceivedCallback.onDataReceived(currentBuffer);
                subscriber.onNext(true);
            }
            receivedData = false;
        }
    }

    public static Observable<List<BluetoothDevice>> getBondedDevices() {
        return Observable.create(new Observable.OnSubscribe<List<BluetoothDevice>>() {
            @Override
            @RequiresPermission(anyOf = {BLUETOOTH_ADMIN, BLUETOOTH})
            public void call(Subscriber<? super List<BluetoothDevice>> subscriber) {

                if (!getDefaultAdapter().isEnabled()) getDefaultAdapter().enable();

                List<BluetoothDevice> listIsBetterThanSet = new ArrayList<>();
                listIsBetterThanSet.addAll(getDefaultAdapter().getBondedDevices());

                subscriber.onNext(listIsBetterThanSet);
                subscriber.onCompleted();
            }
        });
    }

    public Observable<BluetoothSocket> initCommunication(final BluetoothDevice device, final ConnectionSecurity security) {
        return Observable.create(new Observable.OnSubscribe<BluetoothSocket>() {
            @Override
            @RequiresPermission(anyOf = {BLUETOOTH, BLUETOOTH_ADMIN})
            public void call(Subscriber<? super BluetoothSocket> subscriber) {
                if (!getDefaultAdapter().isEnabled()) getDefaultAdapter().enable();
                if (device == null) {
                    subscriber.onError(new Throwable("Device cannot be null."));
                } else {
                    try {
                        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
                        bluetoothSocket = security == ConnectionSecurity.SECURE ?
                                device.createRfcommSocketToServiceRecord(uuid) :
                                device.createInsecureRfcommSocketToServiceRecord(uuid);
                        bluetoothSocket.connect();
                    } catch (IOException e) {
                        subscriber.onError(new Throwable("Failed to connect to device"));
                        return;
                    }
                    subscriber.onNext(bluetoothSocket);
                    subscriber.onCompleted();
                }
            }
        });
    }

    /**
     * Enables connection to selected device
     *
     * @param device               BluetoothDevice
     * @param onConnectedCallback  send first packet of bytes. You can also swich between views here
     * @param dataReceivedCallback inputStream being called every time bluetooth adapter receives bytes from device
     */
    public rx.Subscription connectToDevice(BluetoothDevice device,
                                           final BlueInterfaces.OnConnectedCallback onConnectedCallback,
                                           final BlueInterfaces.DataReceivedCallback dataReceivedCallback) {
        return initCommunication(device, ConnectionSecurity.SECURE).flatMap(new Func1<BluetoothSocket, Observable<Boolean>>() {
            @Override
            public Observable<Boolean> call(BluetoothSocket bluetoothSocket) {
                return getStreamsWork(bluetoothSocket, onConnectedCallback, dataReceivedCallback);
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(Schedulers.newThread())
                .subscribe(new Action1<Boolean>() {
                    @Override
                    public void call(Boolean aBoolean) {
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable e) {
                        if (logLevel != LogLevel.NONE) {
                            Log.e(TAG, "onError " + e.getMessage());
                            e.printStackTrace();
                        }
                        if (onConnectionErrorCallback != null) onConnectionErrorCallback.onError(e);
                    }
                });
    }

    /**
     * Connect to first device of bonded devices.
     * Useful when you have only one device paired.
     * See also {@link BlueDuff#connectToDevice(BluetoothDevice, BlueInterfaces.OnConnectedCallback, BlueInterfaces.DataReceivedCallback)}
     *
     * @param onConnectedCallback  send first packet of bytes. You can also swich between views here
     * @param dataReceivedCallback inputStream being called every time bluetooth adapter receives bytes from device
     */
    @RequiresPermission(BLUETOOTH)
    public rx.Subscription connectToFirstDevice(final BlueInterfaces.OnConnectedCallback onConnectedCallback,
                                                final BlueInterfaces.DataReceivedCallback dataReceivedCallback) {
        if (isVerbose()) Log.d(TAG, "connectToFirstDevice ");
        return BlueDuff.getBondedDevices().flatMap(new Func1<List<BluetoothDevice>, Observable<BluetoothSocket>>() {
            @Override
            @RequiresPermission(BLUETOOTH)
            public Observable<BluetoothSocket> call(List<BluetoothDevice> bluetoothDevices) {
                if (isVerbose()) Log.d(TAG, "call BluetoothSocket observable");
                return initCommunication(bluetoothDevices.get(0), ConnectionSecurity.SECURE);
            }
        }).flatMap(new Func1<BluetoothSocket, Observable<Boolean>>() {
            @Override
            public Observable<Boolean> call(BluetoothSocket bluetoothSocket) {
                if (isVerbose()) Log.d(TAG, "call BluetoothSocket");
                return getStreamsWork(bluetoothSocket, onConnectedCallback, dataReceivedCallback);
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(Schedulers.newThread())
                .subscribe(
                        new Action1<Boolean>() {
                            @Override
                            public void call(Boolean aBoolean) {

                            }
                        }, new Action1<Throwable>() {
                            @Override
                            public void call(Throwable e) {
                                if (logLevel != LogLevel.NONE)
                                    e.printStackTrace();
                                if (onConnectionErrorCallback != null) onConnectionErrorCallback.onError(e);

                            }
                        });
    }
}
