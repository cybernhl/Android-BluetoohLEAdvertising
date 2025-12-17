package com.tutsplus.bleadvertising;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;

import androidx.appcompat.app.AppCompatActivity;

import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.tutsplus.bleadvertising.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        final ParcelUuid demouuid = new ParcelUuid(UUID.fromString(getString(R.string.ble_uuid)));
        final Handler handler = new Handler();
        final BluetoothAdapter bluetoothadapter = BluetoothAdapter.getDefaultAdapter();
        binding.discoverBtn.setOnClickListener(v -> {
            final BluetoothLeScanner scanner = bluetoothadapter.getBluetoothLeScanner();
            final List<ScanFilter> filters = new ArrayList<ScanFilter>();
            final ScanFilter filter = new ScanFilter.Builder()
                    .setServiceUuid(demouuid)
                    .build();
            filters.add(filter);

            final ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            final ScanCallback scanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    if (result == null
                            || result.getDevice() == null
                            || TextUtils.isEmpty(result.getDevice().getName()))
                        return;

                    final StringBuilder builder = new StringBuilder(result.getDevice().getName());

                    builder.append("\n").append(new String(result.getScanRecord().getServiceData(result.getScanRecord().getServiceUuids().get(0)), Charset.forName("UTF-8")));

                    binding.text.setText(builder.toString());
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    super.onBatchScanResults(results);
                }

                @Override
                public void onScanFailed(int errorCode) {
                    Log.e("BLE", "Discovery onScanFailed: " + errorCode);
                    super.onScanFailed(errorCode);
                }
            };
            scanner.startScan(filters, settings, scanCallback);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    scanner.stopScan(scanCallback);
                }
            }, 10000);

        });
        binding.advertiseBtn.setOnClickListener(v -> {
            TestCalculateAdvertiseDataSize();
            final BluetoothLeAdvertiser advertiser = bluetoothadapter.getBluetoothLeAdvertiser();
            final boolean includeTxPower = true;
            final boolean includeDeviceName = false;
            final String deviceName = bluetoothadapter.getName();
            byte[] serviceDataBytes = "0".getBytes(Charset.forName("UTF-8"));
            final AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                    .setConnectable(true)
                    .build();
            final AdvertiseData data = new AdvertiseData.Builder()
                    .setIncludeDeviceName(includeDeviceName)
                    .setIncludeTxPowerLevel(includeTxPower)
                    .addServiceUuid(demouuid)
                    .build();
            final AdvertiseData response = new AdvertiseData.Builder()
                    .setIncludeDeviceName(true)
                    .build();
            final AdvertiseCallback advertisingCallback = new AdvertiseCallback() {
                @Override
                public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                    super.onStartSuccess(settingsInEffect);
                    Log.i("BLE", "Advertising onStartSuccess: " + settingsInEffect);
                }

                @Override
                public void onStartFailure(int errorCode) {
                    Log.e("BLE", "Advertising onStartFailure: " + errorCode);
                    super.onStartFailure(errorCode);
                    switch (errorCode) {
                        case ADVERTISE_FAILED_ALREADY_STARTED:
                            Log.e("BLE", "App was already advertising");
                            break;
                        case ADVERTISE_FAILED_DATA_TOO_LARGE:
                            Log.e("BLE", "DATA_TOO_LARGE");
                            break;
                        case ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                            Log.e("BLE", "FEATURE_UNSUPPORTED");
                            break;
                        case ADVERTISE_FAILED_INTERNAL_ERROR:
                            Log.e("BLE", "INTERNAL_ERROR");
                            break;
                        case ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                            Log.e("BLE", "TOO_MANY_ADVERTISERS");
                            break;
                        default:
                            Log.wtf("BLE", "Unhandled error: " + errorCode);
                    }
                }
            };
            advertiser.startAdvertising(settings, data, response, advertisingCallback);
        });
        if (!bluetoothadapter.isMultipleAdvertisementSupported()) {
            Toast.makeText(getBaseContext(), "Multiple advertisement not supported", Toast.LENGTH_SHORT).show();
            binding.advertiseBtn.setEnabled(false);
            binding.discoverBtn.setEnabled(false);
        }
    }

    private void TestCalculateAdvertiseDataSize(){
        // --- 測試區塊：這段程式碼只用來驗證計算函式的正確性 ---
        Log.d("BLE_TEST", "--- Running Calculation Tests ---");
        final ParcelUuid demouuid = new ParcelUuid(UUID.fromString("CDB7950D-73F1-4D4D-8E47-C090502DBD63"));
        final ParcelUuid batteryServiceUuid = new ParcelUuid(UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb"));
        byte[] batteryLevelData = new byte[] { 98 }; // 假設電量 98%

        // 測試1：只有 16-bit UUID 的 Service Data
        int sizeWith16Bit = calculateAdvertiseDataSize(
                false, // includeDeviceName
                false, // includeTxPower
                batteryServiceUuid, // The UUID for the service data
                batteryLevelData  // The service data itself
        );
        // 預期結果: 3 (Flags) + [1(Len) + 1(Type) + 2(UUID) + 1(Data)] = 3 + 5 = 8 bytes
        Log.d("BLE_TEST", "16-bit Service Data ONLY: " + sizeWith16Bit + " bytes (Expected: 8)");

        // 測試2：只有 128-bit UUID 的 Service Data
        int sizeWith128Bit = calculateAdvertiseDataSize(
                false, // includeDeviceName
                false, // includeTxPower
                demouuid, // The UUID for the service data
                batteryLevelData // The service data itself
        );
        // 預期結果: 3 (Flags) + [1(Len) + 1(Type) + 16(UUID) + 1(Data)] = 3 + 19 = 22 bytes
        Log.d("BLE_TEST", "128-bit Service Data ONLY: " + sizeWith128Bit + " bytes (Expected: 22)");
        Log.d("BLE_TEST", "--- End of Calculation Tests ---");
    }

    /**
     * Pre-calculates the size of the AdvertiseData payload.
     */
    private int calculateAdvertiseDataSize(boolean includeDeviceName, boolean includeTxPower, ParcelUuid uuid, byte[] serviceData) {
        // Flags are always added by the builder.
        int size = 3; // 1 (length) + 1 (type) + 1 (data) for Flags

        // 2. Tx Power Level
        if (includeTxPower) {
            // 1 (length) + 1 (type) + 1 (data)
            size += 3;
        }

        // 3. Device Name
        if (includeDeviceName) {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            String deviceName = adapter.getName();
            if (deviceName != null && !deviceName.isEmpty()) {
                size += (2 + deviceName.getBytes(Charset.forName("UTF-8")).length); // 1 for length, 1 for type
            }
        }
        // 4. Service UUID (from .addServiceUuid)
        // --- 計算 Service UUID (AD Type 0x07) ---
        // This is added as a separate field by the builder.
        if (uuid != null) {
            // AD Structure: [Length] [Type 0x07] [UUID Data]
            // This calculation assumes a list of 128-bit UUIDs.
            size += 18; // 1 (Length) + 1 (Type) + 16 (UUID)
        }

        // 5. Service Data (from .addServiceData)
        // --- 計算 Service UUID (AD Type 0x07) ---
        // --- 計算 Service Data (AD Type 0x21) ---
        if (serviceData != null && uuid != null) {
            // AD Structure: [Length] [Type] [Service Data UUID] [Service Data]
            // Service Data: 1 (length) + 1 (type) + 2 (UUID for data) + data length
            // Length = 1 (Type) + 16 (128-bit UUID) + serviceData.length
            // Total size = 1 (Length byte) + Length
            // Note: For ServiceData, the UUID is often shortened to 16-bit if possible,
            // but let's assume the full UUID is part of the data identification.
            // A more precise calculation depends on the AD type used (0x16 for 16-bit UUID, etc.)
            // For simplicity, let's just add the data length plus overhead.
//            size += (1 + 1 + 2 + serviceData.length);
            int uuidSizeInServiceData  = getServiceDataUuidSize(uuid); // Can be 2, 4, or 16
            // The length of the data part of the AD Structure
            // (1 for AD Type + size of UUID + size of data)
            int dataPartLength = 1 + uuidSizeInServiceData + serviceData.length;
            // The total size for this structure is 1 (for the length field) + dataPartLength
            size += (1 + dataPartLength);
        }
        return size;
    }

    /**
     * Helper method to determine the byte size of a ParcelUuid for service data.
     * Returns 2 for 16-bit, 4 for 32-bit, and 16 for 128-bit UUIDs.
     */
    private int getServiceDataUuidSize(ParcelUuid parcelUuid) {
        final UUID uuid = parcelUuid.getUuid();
        long lsb = uuid.getLeastSignificantBits();
        long msb = uuid.getMostSignificantBits();
        final long BLE_BASE_UUID_LSB = 0x800000805F9B34FBL;
        final long BLE_BASE_UUID_MSB = 0x0000000000001000L;

        if (lsb == BLE_BASE_UUID_LSB && (msb & 0xFFFF0000FFFFFFFFL) == BLE_BASE_UUID_MSB) {
            // This is a 16-bit or 32-bit UUID
            if ((msb & 0x0000FFFF00000000L) == 0) {
                return 2; // 16-bit
            } else {
                return 4; // 32-bit
            }
        }
        return 16; // 128-bit
    }
}
