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
public class SampleGattAttributes {
    private static HashMap<String, String> attributes = new HashMap();
    public static final String PRIVATE_SERVICE_UUID_STRING =
            "11223344-5566-7788-9900-aabbccddeeff";
    public static final UUID PRIVATE_SERVICE_UUID =
            UUID.fromString(PRIVATE_SERVICE_UUID_STRING);
    public static final String SENSOR_CHARACTERISTIC_UUID_STRING =
            "01020304-0506-0708-0900-0a0b0c0d0e0f";
    public static final UUID SENSOR_CHARACTERISTIC_UUID =
            UUID.fromString(SENSOR_CHARACTERISTIC_UUID_STRING);
    public static final String CHARACTERISTIC_CONFIG_UUID_STRING =
            "00002902-0000-1000-8000-00805f9b34fb";
    public static final UUID CHARACTERISTIC_CONFIG_UUID =
            UUID.fromString(CHARACTERISTIC_CONFIG_UUID_STRING);

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

        attributes.put(SENSOR_CHARACTERISTIC_UUID_STRING, "5-byte r notif. sensor value");
        attributes.put("ff020304-0506-0708-0900-0a0b0c0d0e0f", "3-byte rw notif. char.");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
