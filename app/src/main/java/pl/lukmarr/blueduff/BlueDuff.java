package pl.lukmarr.blueduff;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresPermission;
import android.util.Log;

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
 */
public class BlueDuff {
    public InputStream is;
    public OutputStream os;
    public static final String TAG = BlueDuff.class.getSimpleName();
    private final int BUFFER_CAPACITY;
    private final long COLLECT_PACKET_DELAY;
    private byte[] currentBuffer;
    public BluetoothSocket bluetoothSocket;
    private boolean verbose = true;

    public BlueDuff() {
        BUFFER_CAPACITY = 1024;
        COLLECT_PACKET_DELAY = 10;
    }

    /**
     * Disables logs produced by method called from this class.
     * Logs are enabled by default
     */
    public void disableLogs() {
        this.verbose = false;
    }

    public BlueDuff(int bufferCapacity, int collectPacketDelay) {
        BUFFER_CAPACITY = bufferCapacity;
        COLLECT_PACKET_DELAY = collectPacketDelay;
    }

    /**
     * Kills previous streams and socket to avoid leaking resources
     *
     * @param listener - do something after closing living streams
     */
    public void closeStreams(@Nullable final BlueInterfaces.OnSocketKilledCallback listener) {
        rx.Observable.create(new Observable.OnSubscribe<Boolean>() {
            @Override
            public void call(Subscriber<? super Boolean> subscriber) {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException iex) {
                        subscriber.onError(new Throwable("Failed to close input stream"));
                    }
                    is = null;
                }
                if (os != null) {
                    try {
                        os.close();
                    } catch (IOException iex) {
                        subscriber.onError(new Throwable("Failed to close output stream"));
                    }
                    os = null;
                }
                if (bluetoothSocket != null) {
                    try {
                        bluetoothSocket.close();
                    } catch (IOException blex) {
                        subscriber.onError(new Throwable("Failed to close socket"));
                    }
                    bluetoothSocket = null;
                }
                subscriber.onNext(true);
                subscriber.onCompleted();
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
                        if (verbose) {
                            Log.e(TAG, throwable.getMessage());
                            throwable.printStackTrace();
                        }
                    }
                });
    }


    private int write(byte[] bytes) {
        if (os != null) {
            try {
                os.write(bytes);
            } catch (Exception e) {
                return 0;
            }
            return bytes.length;
        } else {
            if (verbose)
                Log.e(TAG, "Output stream not opened. Create connection first!");
            return 0;
        }
    }

    /**
     * @param bytes data to send
     * @return true, if output stream is opened
     */
    public boolean writeData(byte[] bytes) {
        if (verbose)
            Log.d(TAG, "writeData here");
        boolean canWrite = os != null;
        if (canWrite) Observable.just(write(bytes))
                .subscribeOn(Schedulers.io())
                .subscribe(new Action1<Integer>() {
                    @Override
                    public void call(Integer integer) {
                        if (verbose)
                            Log.d(TAG, integer + " bytes written!!!");
                    }
                });
        return canWrite;
    }

    /**
     * @param socket               BluetoothSocket passed via rx chain
     * @param firstWriter          callback where you can do something, after connection is ready
     * @param dataReceivedCallback handle data from input stream
     * @return
     */
    public Observable<Boolean> getStreamsWork(final BluetoothSocket socket,
                                              @Nullable final BlueInterfaces.OnConnectedCallback firstWriter,
                                              @NonNull final BlueInterfaces.DataReceivedCallback dataReceivedCallback) {
        return Observable.create(new Observable.OnSubscribe<Boolean>() {
            boolean receivedData = false;

            @Override
            public void call(Subscriber<? super Boolean> subscriber) {
                boolean communicationNotCrashed = true;
                try {
                    is = socket.getInputStream();
                    os = socket.getOutputStream();
                } catch (Exception f) {
                    subscriber.onError(new Throwable("error on opening input stream"));
                } finally {
                    if (is != null && os != null) {
                        if (firstWriter != null) firstWriter.onConnected();
                        byte[] buffer = new byte[BUFFER_CAPACITY];
                        int bytesReceived;
                        while (communicationNotCrashed) {
                            try {
                                Thread.sleep(COLLECT_PACKET_DELAY);
                                bytesReceived = is.available();
                                if (bytesReceived > 0) {
                                    bytesReceived = is.read(buffer);
                                    byte[] newBuffer = new byte[bytesReceived];
                                    System.arraycopy(buffer, 0, newBuffer, 0, bytesReceived);
                                    if (verbose) Log.d(TAG, "bytes count : " + bytesReceived);
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
                                    if (verbose) Log.d(TAG, "received data = true");
                                } else {
                                    if (receivedData) {
                                        if (verbose)
                                            Log.d(TAG, "received data: " + currentBuffer.length + " bytes");
                                        dataReceivedCallback.onDataReceived(currentBuffer);
                                        subscriber.onNext(true);
                                    }
                                    receivedData = false;
                                }
                            } catch (Exception x) {
                                communicationNotCrashed = false;
                                subscriber.onError(new Throwable("error during reading input stream"));
                            }
                        }
                        subscriber.onCompleted();
                    } else subscriber.onError(new Throwable("i/o crashed"));
                }
            }
        });
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

    public Observable<BluetoothSocket> initCommunication(final BluetoothDevice device, final boolean isSecureRfcomm) {
        return Observable.create(new Observable.OnSubscribe<BluetoothSocket>() {
            @Override
            @RequiresPermission(anyOf = {BLUETOOTH, BLUETOOTH_ADMIN})
            public void call(Subscriber<? super BluetoothSocket> subscriber) {
                if (!getDefaultAdapter().isEnabled())
                    getDefaultAdapter().enable();
                if (device == null) {
                    subscriber.onError(new Throwable("Device cannot be null."));
                } else {
                    try {
                        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
                        bluetoothSocket = isSecureRfcomm ?
                                device.createRfcommSocketToServiceRecord(uuid) :
                                device.createInsecureRfcommSocketToServiceRecord(uuid);
                        bluetoothSocket.connect();
                    } catch (IOException e) {
                        if (verbose) e.printStackTrace();
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
     * @param dataReceivedCallback is being called every time bluetooth adapter receives bytes from device
     */
    public void connectToDevice(BluetoothDevice device,
                                final BlueInterfaces.OnConnectedCallback onConnectedCallback,
                                final BlueInterfaces.DataReceivedCallback dataReceivedCallback) {
        initCommunication(device, true).flatMap(new Func1<BluetoothSocket, Observable<Boolean>>() {
            @Override
            public Observable<Boolean> call(BluetoothSocket bluetoothSocket) {
                return getStreamsWork(bluetoothSocket, onConnectedCallback, dataReceivedCallback);
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(Schedulers.newThread())
                .subscribe(new Subscriber<Boolean>() {
                    @Override
                    public void onCompleted() {
                        if (verbose) Log.d(TAG, "onCompleted ");
                    }

                    @Override
                    public void onError(Throwable e) {
                        if (!verbose) return;
                        Log.e(TAG, "onError " + e.getMessage());
                        e.printStackTrace();
                    }

                    @Override
                    public void onNext(Boolean aBoolean) {
                        if (verbose) Log.d(TAG, "onNext " + aBoolean);
                    }
                });
    }

    /**
     * Connect to first device of bonded devices.
     * Useful when you have only one device paired.
     * See also {@link BlueDuff#connectToDevice(BluetoothDevice, BlueInterfaces.OnConnectedCallback, BlueInterfaces.DataReceivedCallback)}
     *
     * @param onConnectedCallback  send first packet of bytes. You can also swich between views here
     * @param dataReceivedCallback is being called every time bluetooth adapter receives bytes from device
     */
    @RequiresPermission(BLUETOOTH)
    public void connectToFirstDevice(final BlueInterfaces.OnConnectedCallback onConnectedCallback,
                                     final BlueInterfaces.DataReceivedCallback dataReceivedCallback) {
        if (verbose) Log.d(TAG, "connectToFirstDevice ");
        BlueDuff.getBondedDevices().flatMap(new Func1<List<BluetoothDevice>, Observable<BluetoothSocket>>() {
            @Override
            @RequiresPermission(BLUETOOTH)
            public Observable<BluetoothSocket> call(List<BluetoothDevice> bluetoothDevices) {
                if (verbose) Log.d(TAG, "call BluetoothSocket observable");
                return initCommunication(bluetoothDevices.get(0), true);
            }
        }).flatMap(new Func1<BluetoothSocket, Observable<Boolean>>() {
            @Override
            public Observable<Boolean> call(BluetoothSocket bluetoothSocket) {
                if (verbose) Log.d(TAG, "call BluetoothSocket");
                return getStreamsWork(bluetoothSocket, onConnectedCallback, dataReceivedCallback);
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(Schedulers.newThread())
                .subscribe(new Subscriber<Boolean>() {
                    @Override
                    public void onCompleted() {
                        if (verbose) Log.d(TAG, "onCompleted ");
                    }

                    @Override
                    public void onError(Throwable e) {
                        if (!verbose) return;
                        Log.e(TAG, "onError " + e.getMessage());
                        e.printStackTrace();
                    }

                    @Override
                    public void onNext(Boolean aBoolean) {
                        if (verbose) Log.d(TAG, "onNext " + aBoolean);
                    }
                });
    }
}
