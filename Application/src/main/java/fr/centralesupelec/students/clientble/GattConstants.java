/*
 * Copyright (C) 2013 The Android Open Source Project
 * Copyright (C) 2017 Louis-Guillaume Dubois
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

import java.util.HashMap;
import java.util.UUID;

/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 */
public class GattConstants {
    private static HashMap<String, String> attributes = new HashMap();

    // UUID de notre service privé
    // Version 4 (random) UUID — https://www.uuidgenerator.net/
    public static final String PRIVATE_SERVICE_UUID_STRING =
            "622df401-85ed-4666-a4fe-9efaa3ab47aa";
    public static final UUID PRIVATE_SERVICE_UUID =
            UUID.fromString(PRIVATE_SERVICE_UUID_STRING);

    // UUID (v4, cf. supra) de notre caractéristique privée lisible et notifiable, de deux octets.
    public static final String SENSOR_CHARACTERISTIC_UUID_STRING =
            "7817a8eb-f6cb-4be3-8143-52086719754d";
    public static final UUID SENSOR_CHARACTERISTIC_UUID =
            UUID.fromString(SENSOR_CHARACTERISTIC_UUID_STRING);

    // UUID (v4, cf. supra) de notre caractéristiuqe privée, lisible, éditable et notifiable,
    // de trois octets.
    public static final String WRITABLE_CHARACTERISTIC_UUID_STRING =
            "c093685d-005f-4d3c-8240-6d3020a2c608";
    public static final UUID WRITABLE_CHARACTERISTIC_UUID =
            UUID.fromString(WRITABLE_CHARACTERISTIC_UUID_STRING);

    // UUID du descripteur qui donne la configuration client d’une caractérisitique
    // (notification ou non.)
    public static final String CHARACTERISTIC_CONFIG_UUID_STRING =
            "00002902-0000-1000-8000-00805f9b34fb";
    public static final UUID CHARACTERISTIC_CONFIG_UUID =
            UUID.fromString(CHARACTERISTIC_CONFIG_UUID_STRING);

    // UUID de services connus — utilisés dans DeviceControlActivity
    static {
        // Sample Services.
        attributes.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information");
        attributes.put("00001801-0000-1000-8000-00805f9b34fb", "Generic Attribute");
        attributes.put("00001800-0000-1000-8000-00805f9b34fb", "Generic Access");
        attributes.put("0000180f-0000-1000-8000-00805f9b34fb", "Battery Service");
        attributes.put(PRIVATE_SERVICE_UUID_STRING, "Private Service");

        // Sample Characteristics.
        attributes.put("00002a19-0000-1000-8000-00805f9b34fb", "Battery Level");
        attributes.put("00002a00-0000-1000-8000-00805f9b34fb", "Device Name");
        attributes.put("00002a01-0000-1000-8000-00805f9b34fb", "Appearance");
        attributes.put("00002a04-0000-1000-8000-00805f9b34fb", "Peripheral Preferred Connection Parameters");
        attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name");
        attributes.put(SENSOR_CHARACTERISTIC_UUID_STRING, "Sensor value");
        attributes.put(WRITABLE_CHARACTERISTIC_UUID_STRING, "3-byte rw notif. char.");
    }

    // recherche du nom de services connus — utilisé dans DeviceControlActivity
    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
