package pl.lukmarr.blueduff;

import java.util.UUID;

/**
 * Created by Łukasz Marczak
 * <p/>
 * Means if communication with device should be connected
 * secure:
 * {@link android.bluetooth.BluetoothDevice#createRfcommSocketToServiceRecord(UUID)}
 * or insecure:
 * {@link android.bluetooth.BluetoothDevice#createInsecureRfcommSocketToServiceRecord(UUID)}
 * <p/>
 * By default, connection is SECURE
 *
 * @since 01.03.16
 */
public enum ConnectionSecurity {
    SECURE, INSECURE

}
