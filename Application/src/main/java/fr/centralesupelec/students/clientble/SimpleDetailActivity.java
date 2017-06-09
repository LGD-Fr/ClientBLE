/*
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

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

/**
 * Activité qui se connecte à un appareil BLE proposant notre service privé et
 * affiche les caractéristiques de ce dernier.
 * Permet la modification de la caractéristique longue éditable, et écoute
 * les notifications de la valeur du potentiomètre.
 */
public class SimpleDetailActivity extends Activity {
    private final static String TAG = SimpleDetailActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    /* Référence à des objets de l’interface. */
    private TextView mDeviceAddressView;
    private TextView mConnectionStateView;
    private TextView mSensorValueView;
    private TextView mWritableValueView;
    private TextView mFormView;

    private String mDeviceName;
    private String mDeviceAddress;

    private BluetoothLeService mBluetoothLeService;
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mSensorValueCharac;
    private BluetoothGattCharacteristic mWritableValueCharac;


    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_SENSOR_VALUE_AVAILABLE: received data (sensor value) from the device.  This can be a result of read
    //                        or notification operations.
    // ACTION_WRITABLE_VALUE_AVAILABLE: received data (long characteristic value) from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                Log.d(TAG, "ACTION_GATT_SERVICES_DISCOVERED reçu.");
                requestValues(); // Demande des valeurs des caractéristiques.
            } else if (BluetoothLeService.ACTION_SENSOR_VALUE_AVAILABLE.equals(action)) {
                Log.d(TAG, "ACTION_SENSOR_VALUE_AVAILABLE reçu.");
                final String data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                Log.d(TAG, data);
                displaySensorValue(data); // Affichage de la nouvelle valeur.
            } else if (BluetoothLeService.ACTION_WRITABLE_VALUE_AVAILABLE.equals(action)) {
                Log.d(TAG, "ACTION_WRITABLE_VALUE_AVAILABLE reçu.");
                final String data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                Log.d(TAG, data);
                displayWritableValue(data); // Affichage de la nouvelle valeur.
            } else {
                Log.w(TAG, "Action non reconnue.");
            }
        }
    };

    /**
     * Nettoyage de l’interface.
     */
    private void clearUI() {
        mSensorValueView.setText(R.string.no_data);
        mWritableValueView.setText(R.string.no_data);
    }

    /**
     * Méthode appelée lors de la création de l’activité ou de la modification de l’orientation
     * de l’écran (paysage/portrait.)
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.simple_detail_layout);

        // Réception des données de l’Intent qui a lancée l’activité.
        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Configuration des références vers les objets de l’interface
        mDeviceAddressView = (TextView) findViewById(R.id.device_address);
        mDeviceAddressView.setText(mDeviceAddress);

        mFormView = (TextView) findViewById(R.id.form_view);
        mConnectionStateView = (TextView) findViewById(R.id.connection_state);
        mSensorValueView = (TextView) findViewById(R.id.sensor_value);
        mWritableValueView = (TextView) findViewById(R.id.writable_value);

        // Configuration de la barre supérieure
        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        // Liaison au service BluetoothLeService
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }


    /**
     * Méthode appelée après onCreate, ou quand l’application repasse au premier plan.
     */
    @Override
    protected void onResume() {
        super.onResume();
        // Recevoir à nouveau les notifications de la valeur du potentiomètre.
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
            mBluetoothLeService.setCharacteristicNotification(mSensorValueCharac, true);
        }
    }


    /**
     * Méthode appelée avant onDestroy, ou quand l’application passe à l’arrière-plan.
     */
    @Override
    protected void onPause() {
        super.onPause();
        // Cesser de recevoir les notifications de la valeur du potentiomètre.
        mBluetoothLeService.setCharacteristicNotification(mSensorValueCharac, false);
        unregisterReceiver(mGattUpdateReceiver);
    }

    /**
     * Quand l’activité se termine.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Fin de la liaison avec le service
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    /**
     * Gestion de l’appui sur un bouton de la barre supérieure.
     * @param item
     * @return
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            // Bouton à gauche
            case android.R.id.home:
                // Identique à un appel au bouton "retour" en forme de triangle en bas à gauche
                // de la barre inférieure d’Android.
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    /**
     * Affichage de l’était de la connexion.
     * @param resourceId
     */
    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionStateView.setText(resourceId);
            }
        });
    }

    /**
     * Affichage de la valeur du potentiomètre.
     * @param data
     */
    private void displaySensorValue(final String data) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (data != null) {
                    mSensorValueView.setText(data);
                }
            }
        });
    }

    /**
     * Affichage de la valeur de la caractéristique longue éditable.
     * @param data
     */
    private void displayWritableValue(final String data) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (data != null) {
                    mWritableValueView.setText(data);
                }
            }
        });
    }

    /**
     * Lors d’un appui sur le bouton "Read" pour lire de nouveau la valeur de la caractéristique longue.
     * @param view
     */
    public void onRefreshClick(View view) {
        Log.d(TAG, "onRefreshClick()");
        requestWritableValue();
    }

    /**
     * Lors d’un appui sur le bouton "Write" pour envoyer à l’appareil BLE dans la caractéristique
     * longue éditable la valeur (ASCII) du champ textuel au-dessus.
     * @param view
     */
    public void onSendClick(View view) {
        Log.d(TAG, "onSendClick()");
        // Récupération du contenu du champ textuel éditable par l’utilisateur.
        byte [] text = mFormView.getText().toString().getBytes();
        // Longueur maximale de la valeur de la caractéristique: 20 octets.
        byte [] data = new byte[20];
        int max = Math.min(text.length, GattConstants.WRITABLE_CHARACTERISTIC_MAX_LENGTH);
        for (int i = 0; i < max; i++) {
            data[i] = text[i];
        }
        Log.d(TAG, "envoi de: " + data.toString());
        mBluetoothLeService.writeCharacterisitic(mWritableValueCharac, data);
    }

    /**
     * Demande de la valeur de la caractéristique longue éditable.
     */
    private void requestWritableValue() {
        BluetoothGattService privateService = mBluetoothLeService.getPrivateService();
        if (privateService == null) {
            Log.w(TAG, "Service Gatt privé non détecté.");
            return;
        }
        mWritableValueCharac =
                privateService.getCharacteristic(GattConstants.WRITABLE_CHARACTERISTIC_UUID);
        if (mWritableValueCharac != null) {
            mBluetoothLeService.readCharacteristic(mWritableValueCharac);
        } else {
            Log.w(TAG, "WRITABLE_CHARACTERISTIC_UUID non trouvé.");
        }
    }

    /**
     * Demande la valeur du potentiomètre, et souscrit à ses notifications.
     */
    private void requestAndSubscribeSensorValue() {
        BluetoothGattService privateService = mBluetoothLeService.getPrivateService();
        if (privateService == null) {
            Log.w(TAG, "Service Gatt privé non détecté.");
            return;
        }
        mSensorValueCharac =
                privateService.getCharacteristic(GattConstants.SENSOR_CHARACTERISTIC_UUID);
        if (mSensorValueCharac != null) {
            // Lecture.
            mBluetoothLeService.readCharacteristic(mSensorValueCharac);

            // Souscription aux notifications.
            final int charaProp = mSensorValueCharac.getProperties();
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                Log.d(TAG, "Demande de notification.");
                mBluetoothLeService.setCharacteristicNotification(mSensorValueCharac, true);
            }
        } else {
            Log.w(TAG, "SENSOR_CHARACTERISTIC_UUID non trouvé");
        }
    }

    /**
     * Demande les valeurs de deux caractéristiques du service,
     * et souscrit aux notifications de la valeur du potentiomètre.
     */
    private void requestValues() {
        requestWritableValue();
        requestAndSubscribeSensorValue();
    }

    /**
     * Filtre les types d’Intents broadcastés à recevoir, suivant les noms des actions.
     * Ne recevoir que les actions envoyées par BluetoothLeService.
     * @return
     */
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_SENSOR_VALUE_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_WRITABLE_VALUE_AVAILABLE);
        return intentFilter;
    }
}
