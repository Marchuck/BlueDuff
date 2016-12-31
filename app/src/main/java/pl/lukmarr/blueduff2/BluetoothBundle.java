package pl.lukmarr.blueduff2;

/**
 * Project "BlueDuff"
 * <p>
 * Created by Lukasz Marczak
 * on 31.12.2016.
 */

public class BluetoothBundle {
    public final String charset;
    public final long bluetoothSleep;
    public final int bufferCapacity;

    public BluetoothBundle(String charset, long bluetoothSleep, int bufferCapacity) {
        this.charset = charset;
        this.bluetoothSleep = bluetoothSleep;
        this.bufferCapacity = bufferCapacity;
    }
}
