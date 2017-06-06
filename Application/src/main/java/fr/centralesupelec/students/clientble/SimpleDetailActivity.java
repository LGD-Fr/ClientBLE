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
import android.provider.SyncStateContract;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

public class SimpleDetailActivity extends Activity {
    private final static String TAG = SimpleDetailActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

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
    // ACTION_SENSOR_VALUE_AVAILABLE: received data from the device.  This can be a result of read
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
                requestValues();
            } else if (BluetoothLeService.ACTION_SENSOR_VALUE_AVAILABLE.equals(action)) {
                Log.d(TAG, "ACTION_SENSOR_VALUE_AVAILABLE reçu.");
                final String data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                Log.d(TAG, data);
                displaySensorValue(data);
            } else if (BluetoothLeService.ACTION_WRITABLE_VALUE_AVAILABLE.equals(action)) {
                Log.d(TAG, "ACTION_WRITABLE_VALUE_AVAILABLE reçu.");
                final String data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                Log.d(TAG, data);
                displayWritableValue(data);
            } else {
                Log.w(TAG, "Action non reconnue.");
            }
        }
    };

    private void clearUI() {
        mSensorValueView.setText(R.string.no_data);
        mWritableValueView.setText(R.string.no_data);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.simple_detail_layout);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        mDeviceAddressView = (TextView) findViewById(R.id.device_address);
        mDeviceAddressView.setText(mDeviceAddress);

        mFormView = (TextView) findViewById(R.id.form_view);
        mConnectionStateView = (TextView) findViewById(R.id.connection_state);
        mSensorValueView = (TextView) findViewById(R.id.sensor_value);
        mWritableValueView = (TextView) findViewById(R.id.writable_value);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }


    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
            mBluetoothLeService.setCharacteristicNotification(mSensorValueCharac, true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mBluetoothLeService.setCharacteristicNotification(mSensorValueCharac, false);
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    /*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }
    */

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            /*
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            */
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionStateView.setText(resourceId);
            }
        });
    }

    private void displaySensorValue(String data) {
        if (data != null) {
            mSensorValueView.setText(data);
        }
    }

    private void displayWritableValue(String data) {
        if (data != null) {
            mWritableValueView.setText(data);
        }
    }

    public void onRefreshClick(View view) {
        Log.d(TAG, "onRefreshClick()");
        requestWritableValue();
    }

    public void onSendClick(View view) {
        Log.d(TAG, "onSendClick()");
        byte [] text = mFormView.getText().toString().getBytes();
        byte [] data = new byte[20];
        int max = Math.min(text.length, GattConstants.WRITABLE_CHARACTERISTIC_MAX_LENGTH);
        for (int i = 0; i < max; i++) {
            data[i] = text[i];
        }
        Log.d(TAG, "envoi de: " + data.toString());
        mBluetoothLeService.writeCharacterisitic(mWritableValueCharac, data);
    }

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

    private void requestAndSubsribeSensorValue() {
        BluetoothGattService privateService = mBluetoothLeService.getPrivateService();
        if (privateService == null) {
            Log.w(TAG, "Service Gatt privé non détecté.");
            return;
        }
        mSensorValueCharac =
                privateService.getCharacteristic(GattConstants.SENSOR_CHARACTERISTIC_UUID);
        if (mSensorValueCharac != null) {
            mBluetoothLeService.readCharacteristic(mSensorValueCharac);
            final int charaProp = mSensorValueCharac.getProperties();
            mBluetoothLeService.readCharacteristic(mSensorValueCharac);
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                Log.d(TAG, "Demande de notification.");
                mBluetoothLeService.setCharacteristicNotification(mSensorValueCharac, true);
            }
        } else {
            Log.w(TAG, "SENSOR_CHARACTERISTIC_UUID non trouvé");
        }

    }

    private void requestValues() {
        requestWritableValue();
        requestAndSubsribeSensorValue();
    }

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
