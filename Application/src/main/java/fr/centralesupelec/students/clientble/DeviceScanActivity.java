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

import android.Manifest;
import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Activité qui scanne et affiche les appareils Bluetooth Low Energy
 * offrant un service BLE particulier.
 *
 * Le scan ne cherche que les appareils qui proposent notre service privé
 * de visualisation de la valeur du potentiomètre de la carte Curiosity
 * et d’écriture d’une valeur.
 */
public class DeviceScanActivity extends ListActivity {

    /* Attributs. */
    private LeDeviceListAdapter mLeDeviceListAdapter; // adapte la liste des appareils scanné pour les afficher sur l’interface
    private BluetoothAdapter mBluetoothAdapter; // représente l’interface BLE, permet le scan
    private boolean mScanning; // si l’activité est en train de scanner
    private Handler mHandler; // gestionnaire de tâches

    /* Constantes : valeurs de retour pour onActivityResult */
    private static final int REQUEST_ENABLE_COARSE_LOCATION = 2;
    private static final int REQUEST_ENABLE_BT = 1;

    /* Constante : durée maximale du scan. */
    private static final long SCAN_PERIOD = 10000;

    /**
     * Méthode appelé lors de la création de l’activité, ou lors d’un changement
     * de l’orientation de l’écran (portrait/paysage.)
     * @param savedInstanceState
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setTitle(R.string.title_devices);
        mHandler = new Handler();

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Demande de la permission d’accès à la localisation, pour permettre le scan BLE.
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        REQUEST_ENABLE_COARSE_LOCATION);

                // REQUEST_ENABLE_ACCESS_COARSE_LOCATION is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }
    }

    /**
     * Méthode de construction du menu (les boutons) de la barre supérieure.
     * @param menu
     * @return
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Affiche les boutons définis dans res/menu/main.xml
        getMenuInflater().inflate(R.menu.main, menu);

        // Sans scan en cours, n’affiche que le bouton "scan"
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            // Si un scan est en cours, affiche un bouton de progression et
            // le bouton "stop", mais pas le bouton "scan".
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.actionbar_indeterminate_progress);
        }
        return true;
    }

    /**
     * Méthode appelée lors de la pression sur un bouton de
     * la barre supérieure.
     * @param item
     * @return
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // apppui sur "scan"
            case R.id.menu_scan:
                mLeDeviceListAdapter.clear(); // vider la liste des appareils affichés
                scanLeDevice(true); // démarrage du scan
                break;
            // appui sur "stop"
            case R.id.menu_stop:
                scanLeDevice(false); // arrêt du scan
                break;
        }
        return true;
    }

    /**
     * Appelée après onCreate() ou lors du retour de l’activité au premier plan.
     */
    @Override
    protected void onResume() {
        super.onResume();

        // Vérifie si le service de localisation est actif sur l’appareil.
        boolean location_enabled;
        try {
            location_enabled = Settings.Secure.LOCATION_MODE_OFF !=
                    Settings.Secure.getInt(
                            getContentResolver(),
                            Settings.Secure.LOCATION_MODE
                    );
        } catch (Exception e) {
            e.printStackTrace();
            location_enabled = false;
        }

        // Si le service de localisation n’est pas activé,
        // demande de l’activer dans les paramètres du système.
        if (!location_enabled) {
            Toast.makeText(this, "Prière d’activer une source de localisation.", Toast.LENGTH_LONG).show();
            Intent enableLocationIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(enableLocationIntent);
        }

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // Initializes list view adapter.
        mLeDeviceListAdapter = new LeDeviceListAdapter();
        setListAdapter(mLeDeviceListAdapter);
        scanLeDevice(true);
    }

    /**
     * Méthode appelé lors du retour depuis une activité lancée avec startActivityForResult.
     * Traitement de la valeur reçue (résultat) envoyée par cette activité.
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            // Application inutile sans BLE => arrêt.
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Méthode appelée lors du passage de l’activité à l’arrière-plan.
     */
    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
        mLeDeviceListAdapter.clear();
    }

    /**
     * Méthode appelée lorsque l’utilisateur clique sur un élément de la liste des appareils
     * détectés par le scan.
     * @param l
     * @param v
     * @param position
     * @param id
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        // Récupération de l’objet représentant l’appareil BLE.
        final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);

        if (device == null) return;

        // Lancement de l’activité "SimpleDetailActivity" pour se connecter à l’appareil
        // et utiliser ses services BLE.
        final Intent intent = new Intent(this, SimpleDetailActivity.class);
        intent.putExtra(SimpleDetailActivity.EXTRAS_DEVICE_NAME, device.getName());
        intent.putExtra(SimpleDetailActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
        if (mScanning) {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            mScanning = false;
        }
        startActivity(intent);
    }

    /**
     * Méthode qui lance ou arrête le scan des appareils BLE à notre portée qui proposent notre service
     * privé.
     * @param enable
     */
    private void scanLeDevice(final boolean enable) {
        // Liste (de taille 1) qui contient l’UUID de notre service privé.
        UUID [] uuids = {GattConstants.PRIVATE_SERVICE_UUID};

        // Démarrage du scan.
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(uuids, mLeScanCallback);
        } else {
            // Arrêt du scan.
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }

    // Adapter for holding devices found through scanning.
    // Contient les appareils détectés lors du scan et permet de les afficher sur
    // l’interface.
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<BluetoothDevice>();
            mInflator = DeviceScanActivity.this.getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {
            if(!mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText(R.string.unknown_device);
            viewHolder.deviceAddress.setText(device.getAddress());

            return view;
        }
    }

    /**
     * Classe définissant la fonction appelée lorsqu’un nouvel appareil est détecté
     * lors d’un scan BLE.
     */
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mLeDeviceListAdapter.addDevice(device);
                    mLeDeviceListAdapter.notifyDataSetChanged();
                }
            });
        }
    };

    /**
     * Classe définissant les données à afficher sur chaque élément de la liste de l’interface.
     */
    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }
}