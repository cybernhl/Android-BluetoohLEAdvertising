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

        if (includeTxPower) {
            // 1 (length) + 1 (type) + 1 (data)
            size += 3;
        }

        if (uuid != null) {
            // Service UUID: 128-bit UUID is 16 bytes
            // 1 (length) + 1 (type) + 16 (data)
            size += 18;
        }

        if (includeDeviceName) {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            String deviceName = adapter.getName();
            if (deviceName != null && !deviceName.isEmpty()) {
                // 1 (length) + 1 (type) + name_length_in_bytes
                size += (2 + deviceName.getBytes(Charset.forName("UTF-8")).length);
            }
        }

        // --- 計算 Service UUID (AD Type 0x07) ---
        // 只有當 Service Data 不存在時，單獨的 Service UUID 才會被計算
        // 因為 Service Data 欄位本身已經包含了 UUID
        if (uuid != null && serviceData == null) {
            // Service UUID: 128-bit UUID is 16 bytes
            // 1 (length) + 1 (type) + 16 (data)
            size += 18;
        }
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
            size += (1 + 1 + 2 + serviceData.length);
        }
        return size;
    }
}
