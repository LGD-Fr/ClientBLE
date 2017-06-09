/*
 * Copyright (C) 2013 The Android Open Source Project
 * Copyright (C) 2017 CentraleSupélec
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.centralesupelec.students.clientble;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static java.lang.Thread.sleep;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    // Nom des actions envoyées lors des Intents broadcastés
    public final static String ACTION_GATT_CONNECTED =
            "fr.cenralesupelec.students.clientble.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "fr.centralesupelec.students.clientble.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "fr.centralesupelec.students.clientble.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_SENSOR_VALUE_AVAILABLE =
            "fr.centralesupelec.students.clientble.ACTION_SENSOR_VALUE_AVAILABLE";
    public final static String ACTION_WRITABLE_VALUE_AVAILABLE =
            "fr.centralesupelec.students.clientble.ACTION_WRITABLE_VALUE_AVAILABLE";
    public final static String EXTRA_DATA =
            "fr.centralesupelec.students.clientble.EXTRA_DATA";


    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    // Envoie des Intents broadcastés pour permettre à SimpleDetailActivity de récupérer
    // les valeurs reçues de l’appareil BLE.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        /**
         * Renvoie dans une Intent broadcastée la valeur lue de la caractéristique
         * demandée.
         * @param gatt
         * @param characteristic
         * @param status
         */
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            final UUID uuid = characteristic.getUuid();

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (GattConstants.SENSOR_CHARACTERISTIC_UUID.equals(uuid)) {
                    // Valeur du potentiomètre.
                    broadcastUpdate(ACTION_SENSOR_VALUE_AVAILABLE, characteristic);
                } else if (GattConstants.WRITABLE_CHARACTERISTIC_UUID.equals(uuid)) {
                    // Valeur de la caractéristique longue.
                    broadcastUpdate(ACTION_WRITABLE_VALUE_AVAILABLE, characteristic);
                } else {
                    Log.w(TAG, "UUID non reconnue.");
                }
            }
        }

        /**
         * Renvoie dans une Intent broadcastée la valeur écrite de la caractéristique
         * demandée.
         * @param gatt
         * @param characteristic
         * @param status
         */
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            final UUID uuid = characteristic.getUuid();
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Réusssite de l’écriture de la caractéristique.");
                if (GattConstants.SENSOR_CHARACTERISTIC_UUID.equals(uuid)) {
                    // Valeur du potentiomètre – ne devrait pas se produire (lecture seule.)
                    broadcastUpdate(ACTION_SENSOR_VALUE_AVAILABLE, characteristic);
                } else if (GattConstants.WRITABLE_CHARACTERISTIC_UUID.equals(uuid)) {
                    // Valeur de la caractéristique longue et éditable.
                    broadcastUpdate(ACTION_WRITABLE_VALUE_AVAILABLE, characteristic);
                }
            } else {
                Log.w(TAG, "Échec de l’écriture de la caractéristique.");
            }
        }

        /**
         * Renvoie dans une Intent broadcastée la valeur mise à jour d’une caractéristique
         * (en cas de notification par exemple.)
         * @param gatt
         * @param characteristic
         */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "onCharacteristicChanged() appelé.");
            final UUID uuid = characteristic.getUuid();
            if (GattConstants.SENSOR_CHARACTERISTIC_UUID.equals(uuid)) {
                broadcastUpdate(ACTION_SENSOR_VALUE_AVAILABLE, characteristic);
            } else if (GattConstants.WRITABLE_CHARACTERISTIC_UUID.equals(uuid)) {
                broadcastUpdate(ACTION_WRITABLE_VALUE_AVAILABLE, characteristic);
            } else {
                Log.w(TAG, "UUID non reconnue");
            }
        }
    };

    /**
     * Méthode d’envoi d’une Intent broadcastée.
     * @param action nom de l’action
     */
    private void broadcastUpdate(final String action) {
        Log.d(TAG, "broadcastUpdate(String) appelé.");
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    /**
     * Méthode d’envoi d’une Intent broadcastée, avec la valeur d’une caractéristique.
     * @param action nom de l’action
     * @param characteristic caractéristique lue, écrite ou mise à jour (notifiée)
     */
    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {

        final Intent intent = new Intent(action);
        Log.d(TAG, "broadcastUpdate(String, BluetoothGattChar.) appelé.");

        // Valeur brute de la caractéristique.
        final byte[] data = characteristic.getValue();

        if (data != null && data.length > 0) {
            // Si c’est la valeur du potentiomètre
            if (GattConstants.SENSOR_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                // Lecture de l’entier non signé, d’un ou deux octets (lecture par le CAN sur 16 bits.)
                final long value =
                        (data.length == 2) ? (data[0] << 8) & 0x0000ff00 | (data[1] << 0) & 0x000000ff
                                          : (data[0] << 0) & 0x000000ff;
                final long max = 65535; // 2^16 - 1 : valeur maximale (16 bits)
                // Envoi d’un pourcentage
                final double percent = ((double) (100 * value)) / ((double) max);
                // Envoi sous forme d’une chaîne de caractère, avec la date, pour affichage direct.
                final String date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.LONG).format(new Date());
                intent.putExtra(EXTRA_DATA, String.format("%.3f %%\n(%s)", percent, date));
            } else {
                // Sinon, caractéristique longue éditable.
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                // Représentation au format hexadécimal.
                for (byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                Log.d(TAG, String.format(stringBuilder.toString()));
                // Envoi de la représentation ASCII puis sur une autre ligne, en hexadécimal.
                intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
            }
        }
        sendBroadcast(intent);
    }

    /**
     * Classe permettant à une activité d’appeler les méthodes du service.
     */
    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    /**
     * Demande de lien à une activité.
     * @param intent
     * @return
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Arrêt du lien avec une activité.
     * @param intent
     * @return
     */
    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    /**
     * Instance de la classe de liaison avec une activité.
     */
    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Écriture d’une caractéristique sur le serveur BLE de l’appareil connecté.
     * @param characteristic caractéristique où écrire
     * @param data données brutes à envoyer pour écriture
     */
    public void writeCharacterisitic(BluetoothGattCharacteristic characteristic, byte[] data) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        characteristic.setValue(data);
        mBluetoothGatt.writeCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        Log.d(TAG, "setCharacteristicNotification() appelé");
        // Ne pas oublier d’écrire le descripteur de la caractéristique pour que les serveur
        // BLE de l’appareil connecté envoie les notifications.
        if (GattConstants.SENSOR_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(GattConstants.CHARACTERISTIC_CONFIG_UUID);
            descriptor.setValue(
                    enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            );
            try {
                // Parfois plusieurs essais sont nécessaires (l’interface BLE peut être
                // occupée avec d’autres opérations.)
                while (!mBluetoothGatt.writeDescriptor(descriptor))
                    sleep(500);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // Demande à l’appareil Android d’écouter et de prendre en compte les notifications
        // envoyées par l’appareil connecté.
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;
        return mBluetoothGatt.getServices();
    }

    /**
     * Retourne notre service privé.
     * @return
     */
    public BluetoothGattService getPrivateService() {
        if (mBluetoothGatt == null) return null;
        return mBluetoothGatt.getService(GattConstants.PRIVATE_SERVICE_UUID);
    }
}
