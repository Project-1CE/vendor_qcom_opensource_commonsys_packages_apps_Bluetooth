/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.bluetooth.gatt;

import android.bluetooth.BluetoothAssignedNumbers.OrganizationId;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.TransportBlockFilter;

import android.os.ParcelUuid;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * Helper class used to manage advertisement package filters.
 *
 * @hide
 */
/* package */class ScanFilterQueue {
    public static final int TYPE_DEVICE_ADDRESS = 0;
    public static final int TYPE_SERVICE_DATA_CHANGED = 1;
    public static final int TYPE_SERVICE_UUID = 2;
    public static final int TYPE_SOLICIT_UUID = 3;
    public static final int TYPE_LOCAL_NAME = 4;
    public static final int TYPE_MANUFACTURER_DATA = 5;
    public static final int TYPE_SERVICE_DATA = 6;
    public static final int TYPE_TRANSPORT_DISCOVERY_DATA = 7;
    public static final int TYPE_GROUP_AD_TYPE_FILTER = 8;

    // Max length is 31 - 3(flags) - 2 (one byte for length and one byte for type).
    private static final int MAX_LEN_PER_FIELD = 26;

    // Values defined in bluedroid.
    private static final byte DEVICE_TYPE_ALL = 2;

    class Entry {
        public byte type;
        public String address;
        public byte addr_type;
        public byte[] irk;
        public UUID uuid;
        public UUID uuid_mask;
        public String name;
        public int company;
        public int company_mask;
        public byte[] data;
        public byte[] data_mask;
        public int org_id;
        public int tds_flags;
        public int tds_flags_mask;
        public boolean group_filter;
    }

    private Set<Entry> mEntries = new HashSet<Entry>();

    void addDeviceAddress(String address, byte type, byte[] irk) {
        Entry entry = new Entry();
        entry.type = TYPE_DEVICE_ADDRESS;
        entry.address = address;
        entry.addr_type = type;
        entry.irk = irk;
        mEntries.add(entry);
    }

    void addServiceChanged() {
        Entry entry = new Entry();
        entry.type = TYPE_SERVICE_DATA_CHANGED;
        mEntries.add(entry);
    }

    void addUuid(UUID uuid) {
        Entry entry = new Entry();
        entry.type = TYPE_SERVICE_UUID;
        entry.uuid = uuid;
        entry.uuid_mask = new UUID(0, 0);
        mEntries.add(entry);
    }

    void addUuid(UUID uuid, UUID uuidMask) {
        Entry entry = new Entry();
        entry.type = TYPE_SERVICE_UUID;
        entry.uuid = uuid;
        entry.uuid_mask = uuidMask;
        mEntries.add(entry);
    }

    void addSolicitUuid(UUID uuid) {
        Entry entry = new Entry();
        entry.type = TYPE_SOLICIT_UUID;
        entry.uuid = uuid;
        entry.uuid_mask = new UUID(0, 0);
        mEntries.add(entry);
    }

    void addSolicitUuid(UUID uuid, UUID uuidMask) {
        Entry entry = new Entry();
        entry.type = TYPE_SOLICIT_UUID;
        entry.uuid = uuid;
        entry.uuid_mask = uuidMask;
        mEntries.add(entry);
    }

    void addName(String name) {
        Entry entry = new Entry();
        entry.type = TYPE_LOCAL_NAME;
        entry.name = name;
        mEntries.add(entry);
    }

    void addManufacturerData(int company, byte[] data) {
        Entry entry = new Entry();
        entry.type = TYPE_MANUFACTURER_DATA;
        entry.company = company;
        entry.company_mask = 0xFFFF;
        entry.data = data;
        entry.data_mask = new byte[data.length];
        Arrays.fill(entry.data_mask, (byte) 0xFF);
        mEntries.add(entry);
    }

    void addManufacturerData(int company, int companyMask, byte[] data, byte[] dataMask) {
        Entry entry = new Entry();
        entry.type = TYPE_MANUFACTURER_DATA;
        entry.company = company;
        entry.company_mask = companyMask;
        entry.data = data;
        entry.data_mask = dataMask;
        mEntries.add(entry);
    }

    void addServiceData(byte[] data, byte[] dataMask) {
        Entry entry = new Entry();
        entry.type = TYPE_SERVICE_DATA;
        entry.data = data;
        entry.data_mask = dataMask;
        mEntries.add(entry);
    }

    void addTransportDiscoveryData(int orgId, int tdsFlags, int tdsFlagsMask,
            byte[] transportData, byte[] transportDataMask) {
        Entry entry = new Entry();
        entry.type = TYPE_TRANSPORT_DISCOVERY_DATA;
        entry.org_id = orgId;
        entry.tds_flags = tdsFlags;
        entry.tds_flags_mask = tdsFlagsMask;
        entry.data = transportData;
        entry.data_mask = transportDataMask;
        mEntries.add(entry);
    }

    void addGroupFilterEntry(boolean groupFilterEnabled) {
        Entry entry = new Entry();
        entry.type = TYPE_GROUP_AD_TYPE_FILTER;
        entry.group_filter = groupFilterEnabled;
        mEntries.add(entry);
    }

    Entry pop() {
        if (mEntries.isEmpty()) {
            return null;
        }
        Iterator<Entry> iterator = mEntries.iterator();
        Entry entry = iterator.next();
        iterator.remove();
        return entry;
    }

    /**
     * Compute feature selection based on the filters presented.
     */
    int getFeatureSelection() {
        int selc = 0;
        for (Entry entry : mEntries) {
            selc |= (1 << entry.type);
        }
        return selc;
    }

    ScanFilterQueue.Entry[] toArray() {
        return mEntries.toArray(new ScanFilterQueue.Entry[mEntries.size()]);
    }

    /**
     * Add ScanFilter to scan filter queue.
     */
    void addScanFilter(ScanFilter filter) {
        if (filter == null) {
            return;
        }
        if (filter.getDeviceName() != null) {
            addName(filter.getDeviceName());
        }
        if (filter.getDeviceAddress() != null) {
            byte addressType = (byte) filter.getAddressType();
            // If addressType == iADDRESS_TYPE_PUBLIC (0) then this is the original
            // setDeviceAddress(address) API path which provided DEVICE_TYPE_ALL (2) which might map
            // to the stack value for address type of BTM_BLE_STATIC (2)
            // Additionally, we shouldn't confuse device type with address type.
            addDeviceAddress(filter.getDeviceAddress(),
                    ((addressType == 0) ? DEVICE_TYPE_ALL : addressType), filter.getIrk());
        }
        if (filter.getServiceUuid() != null) {
            if (filter.getServiceUuidMask() == null) {
                addUuid(filter.getServiceUuid().getUuid());
            } else {
                addUuid(filter.getServiceUuid().getUuid(), filter.getServiceUuidMask().getUuid());
            }
        }
        if (filter.getServiceSolicitationUuid() != null) {
            if (filter.getServiceSolicitationUuidMask() == null) {
                addSolicitUuid(filter.getServiceSolicitationUuid().getUuid());
            } else {
                addSolicitUuid(filter.getServiceSolicitationUuid().getUuid(),
                        filter.getServiceSolicitationUuidMask().getUuid());
            }
        }
        if (filter.getManufacturerData() != null) {
            if (filter.getManufacturerDataMask() == null) {
                addManufacturerData(filter.getManufacturerId(), filter.getManufacturerData());
            } else {
                addManufacturerData(filter.getManufacturerId(), 0xFFFF,
                        filter.getManufacturerData(), filter.getManufacturerDataMask());
            }
        }
        if (filter.getServiceDataUuid() != null && filter.getServiceData() != null) {
            ParcelUuid serviceDataUuid = filter.getServiceDataUuid();
            byte[] serviceData = filter.getServiceData();
            byte[] serviceDataMask = filter.getServiceDataMask();

            serviceData = concate(serviceDataUuid, serviceData);
            byte[] finalServiceDataMask = null;
            if (serviceData != null) {
                // finalServiceDataMask = ServiceDataUuidMask + ServiceDataMask. To let firmware
                // accurately match service data uuid, ServiceDataUuidMask shall be set to all 0xFF.
                finalServiceDataMask = new byte[serviceData.length];
                Arrays.fill(finalServiceDataMask, (byte) 0xFF);
                if (serviceDataMask != null) {
                    int serviceDataUuidMaskLen =
                            finalServiceDataMask.length - serviceDataMask.length;
                    System.arraycopy(serviceDataMask, 0, finalServiceDataMask,
                            serviceDataUuidMaskLen, serviceDataMask.length);
                }
            }

            if (serviceData != null && finalServiceDataMask != null) {
                addServiceData(serviceData, finalServiceDataMask);
            }
        }

        final TransportBlockFilter transportBlockFilter = filter.getTransportBlockFilter();
        if (transportBlockFilter != null) {
            if (transportBlockFilter.getOrgId()
                    == OrganizationId.WIFI_ALLIANCE_NEIGHBOR_AWARENESS_NETWORKING) {
                addTransportDiscoveryData(transportBlockFilter.getOrgId(),
                        transportBlockFilter.getTdsFlags(), transportBlockFilter.getTdsFlagsMask(),
                        transportBlockFilter.getWifiNanHash(), null);
            } else {
                addTransportDiscoveryData(transportBlockFilter.getOrgId(),
                        transportBlockFilter.getTdsFlags(), transportBlockFilter.getTdsFlagsMask(),
                        transportBlockFilter.getTransportData(),
                        transportBlockFilter.getTransportDataMask());
            }

        }

        if (filter.getGroupFilteringValue()) {
            addGroupFilterEntry(filter.getGroupFilteringValue());
        }
    }

    private byte[] concate(ParcelUuid serviceDataUuid, byte[] serviceData) {
        byte[] uuid = BluetoothUuid.uuidToBytes(serviceDataUuid);

        int dataLen = uuid.length + (serviceData == null ? 0 : serviceData.length);
        // If data is too long, don't add it to hardware scan filter.
        if (dataLen > MAX_LEN_PER_FIELD) {
            return null;
        }
        byte[] concated = new byte[dataLen];
        System.arraycopy(uuid, 0, concated, 0, uuid.length);
        if (serviceData != null) {
            System.arraycopy(serviceData, 0, concated, uuid.length, serviceData.length);
        }
        return concated;
    }
}
