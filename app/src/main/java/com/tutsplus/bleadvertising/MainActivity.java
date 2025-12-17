package com.tutsplus.bleadvertising;

import android.Manifest;
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
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.tutsplus.bleadvertising.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private BluetoothAdapter bluetoothadapter;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                boolean allGranted = true;
                for (Map.Entry<String, Boolean> entry : permissions.entrySet()) {
                    if (!entry.getValue()) {
                        allGranted = false;
                        Log.e("BLE", "權限被拒絕: " + entry.getKey());
                    }
                }

                if (allGranted) {
                    Toast.makeText(this, "所有必要權限已授予", Toast.LENGTH_SHORT).show();
                    checkBluetoothSupportAndEnableButtons();
                } else {
                    Toast.makeText(this, "藍牙相關權限是必須的，請手動開啟", Toast.LENGTH_LONG).show();
                    disableButtons();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        bluetoothadapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothadapter == null) {
            Toast.makeText(this, "此裝置不支援藍牙", Toast.LENGTH_LONG).show();
            disableButtons();
            return;
        }

        checkAndRequestPermissions();

        binding.discoverBtn.setOnClickListener(v -> startBleScan());
        binding.advertiseBtn.setOnClickListener(v -> startBleAdvertising());
    }

    private void startBleScan() {
        // --- 每次點擊都再次確認權限 ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "缺少 BLUETOOTH_SCAN 權限", Toast.LENGTH_SHORT).show();
                checkAndRequestPermissions();
                return;
            }
        } else {
            // 對於舊版 Android，掃描需要定位權限
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "缺少 ACCESS_FINE_LOCATION 權限", Toast.LENGTH_SHORT).show();
                checkAndRequestPermissions();
                return;
            }
        }
        final BluetoothLeScanner scanner = bluetoothadapter.getBluetoothLeScanner();
        final List<ScanFilter> filters = new ArrayList<>();
        final ParcelUuid demouuid = new ParcelUuid(UUID.fromString(getString(R.string.ble_uuid)));
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

                String deviceName;
                try {
                    // *** 修正點 1: 為 result.getDevice().getName() 加上 try-catch ***
                    deviceName = result.getDevice().getName();
                } catch (SecurityException e) {
                    Log.e("BLE", "無法獲取裝置名稱，缺少 BLUETOOTH_CONNECT 權限", e);
                    deviceName = "Unnamed Device";
                }

                if (result == null || result.getDevice() == null || TextUtils.isEmpty(deviceName))
                    return;

                final StringBuilder builder = new StringBuilder(deviceName);
                if (result.getScanRecord() != null && result.getScanRecord().getServiceUuids() != null && !result.getScanRecord().getServiceUuids().isEmpty() && result.getScanRecord().getServiceData(result.getScanRecord().getServiceUuids().get(0)) != null) {
                    builder.append("\n").append(new String(result.getScanRecord().getServiceData(result.getScanRecord().getServiceUuids().get(0)), Charset.forName("UTF-8")));
                }
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

        // *** 修正點 2: 為 scanner.startScan() 加上權限檢查 (雖然上面已檢查，但這是為了消除Lint警告) ***
        scanner.startScan(filters, settings, scanCallback);
        binding.discoverBtn.setEnabled(false); // 開始掃描後暫時禁用按鈕

        handler.postDelayed(() -> {
            try {
                // *** 修正點 3: 為 scanner.stopScan() 加上 try-catch ***
                scanner.stopScan(scanCallback);
                binding.discoverBtn.setEnabled(true); // 停止掃描後重新啟用按鈕
                Log.i("BLE", "掃描已停止");
            } catch (SecurityException e) {
                Log.e("BLE", "無法停止掃描，缺少 BLUETOOTH_SCAN 權限", e);
            }
        }, 10000);
    }

    private void startBleAdvertising() {
        // --- 每次點擊都再次確認權限 ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "缺少 BLUETOOTH_ADVERTISE 權限", Toast.LENGTH_SHORT).show();
                checkAndRequestPermissions();
                return;
            }
        }

        String deviceName = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    deviceName = bluetoothadapter.getName();
                } else {
                    Log.w("BLE", "缺少 BLUETOOTH_CONNECT 權限，無法在廣播中包含裝置名稱。");
                }
            } else {
                deviceName = bluetoothadapter.getName();
            }
        } catch (SecurityException e) {
            Log.e("BLE", "獲取裝置名稱時發生 SecurityException", e);
        }

        // --- 3. 呼叫測試方法，並傳入獲取的名稱 ---
        TestCalculateAdvertiseDataSize(deviceName);

        final BluetoothLeAdvertiser advertiser = bluetoothadapter.getBluetoothLeAdvertiser();
        if (advertiser == null) {
            Toast.makeText(this, "此裝置不支援 BLE 廣播", Toast.LENGTH_SHORT).show();
            return;
        }

        final int appleCompanyId = 0x004C;
        final byte[] manufacturerDataBytes = new byte[]{0x12, 0x02, 0x00, 0x02};
        final ParcelUuid amsServiceUuid = new ParcelUuid(UUID.fromString("89D3502B-0F36-433A-8EF4-C502AD55F8DC"));
        final ParcelUuid serviceDataUuid = new ParcelUuid(UUID.fromString("0000FEA0-0000-1000-8000-00805f9b34fb"));
        final ParcelUuid batteryServiceUuid = new ParcelUuid(UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb"));
        final ParcelUuid deviceInfoServiceUuid = new ParcelUuid(UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb"));
        final ParcelUuid currentTimeServiceUuid = new ParcelUuid(UUID.fromString("00001805-0000-1000-8000-00805f9b34fb"));
        final byte[] serviceDataBytes = new byte[]{0x01};

        final AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build();

        final AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addManufacturerData(appleCompanyId, manufacturerDataBytes)
                .addServiceData(serviceDataUuid, serviceDataBytes)
                .addServiceUuid(batteryServiceUuid)
                .addServiceUuid(deviceInfoServiceUuid)
                .addServiceUuid(currentTimeServiceUuid)
                .build();

        final AdvertiseData response = new AdvertiseData.Builder()
                .setIncludeDeviceName(false) // 名稱可以放在 scan response
                .addServiceUuid(amsServiceUuid)
                .build();

        final AdvertiseCallback advertisingCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
                Log.i("BLE", "廣播成功啟動");
                Toast.makeText(MainActivity.this, "廣播已開始", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onStartFailure(int errorCode) {
                Log.e("BLE", "廣播啟動失敗: " + errorCode);
                Toast.makeText(MainActivity.this, "廣播失敗: " + getAdvertiseError(errorCode), Toast.LENGTH_LONG).show();
            }
        };

        // *** 修正點 4: 為 advertiser.startAdvertising() 加上權限檢查 (雖然上面已檢查，但這是為了消除Lint警告) ***
        advertiser.startAdvertising(settings, data, response, advertisingCallback);
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else { // Android 6 到 11
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toArray(new String[0]));
        } else {
            checkBluetoothSupportAndEnableButtons();
        }
    }

    private void checkBluetoothSupportAndEnableButtons() {
        if (!bluetoothadapter.isMultipleAdvertisementSupported()) {
            Toast.makeText(this, "不支援多重廣播", Toast.LENGTH_SHORT).show();
            disableButtons();
        } else {
            binding.advertiseBtn.setEnabled(true);
            binding.discoverBtn.setEnabled(true);
        }
    }
    private void disableButtons() {
        binding.advertiseBtn.setEnabled(false);
        binding.discoverBtn.setEnabled(false);
    }

    private String getAdvertiseError(int errorCode) {
        switch (errorCode) {
            case AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED: return "ADVERTISE_FAILED_ALREADY_STARTED";
            case AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE: return "ADVERTISE_FAILED_DATA_TOO_LARGE";
            case AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED: return "ADVERTISE_FAILED_FEATURE_UNSUPPORTED";
            case AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR: return "ADVERTISE_FAILED_INTERNAL_ERROR";
            case AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS: return "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS";
            default: return "未知錯誤: " + errorCode;
        }
    }
    private void TestCalculateAdvertiseDataSize(String deviceName) { // << 接收 deviceName 參數
        Log.d("BLE_TEST", "--- Running New Calculation Tests ---");
        if (deviceName == null) {
            Log.w("BLE_TEST", "Device name is not available for calculation. Size involving name will be inaccurate if `includeDeviceName` is true.");
        }

        // --- 準備測試資料 (與之前相同) ---
        final int appleCompanyId = 0x004C;
        final byte[] manufacturerDataBytes = new byte[]{0x12, 0x02, 0x00, 0x02};
        final ParcelUuid serviceDataUuid = new ParcelUuid(UUID.fromString("0000FEA0-0000-1000-8000-00805f9b34fb"));
        final byte[] serviceDataBytes = new byte[]{0x01};
        final ParcelUuid batteryServiceUuid = new ParcelUuid(UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb"));
        final ParcelUuid deviceInfoServiceUuid = new ParcelUuid(UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb"));
        final ParcelUuid currentTimeServiceUuid = new ParcelUuid(UUID.fromString("00001805-0000-1000-8000-00805f9b34fb"));
        final ParcelUuid amsServiceUuid = new ParcelUuid(UUID.fromString("89D3502B-0F36-433A-8EF4-C502AD55F8DC"));

        // --- 測試 1: 計算 `data` 封包的大小 ---
        List<ParcelUuid> dataServiceUuids = new ArrayList<>();
        dataServiceUuids.add(batteryServiceUuid);
        dataServiceUuids.add(deviceInfoServiceUuid);
        dataServiceUuids.add(currentTimeServiceUuid);

        int dataPacketSize = calculateAdvertiseDataSize(
                deviceName, // << 直接傳遞收到的 deviceName
                false,      // includeDeviceName (您在廣播中設定為 false)
                false,      // includeTxPower
                appleCompanyId,
                manufacturerDataBytes,
                dataServiceUuids,
                serviceDataUuid,
                serviceDataBytes
        );
        Log.d("BLE_TEST", "Current 'data' packet size: " + dataPacketSize + " bytes");
        if (dataPacketSize > 31) {
            Log.e("BLE_TEST", "WARNING: 'data' packet is TOO LARGE!");
        }

        // --- 測試 2: 計算 `response` 封包的大小 ---
        List<ParcelUuid> responseServiceUuids = new ArrayList<>();
        responseServiceUuids.add(amsServiceUuid);

        int responsePacketSize = calculateAdvertiseDataSize(
                deviceName, // << 直接傳遞收到的 deviceName
                false,      // includeDeviceName (您在廣播中設定為 false)
                false,      // includeTxPower
                -1,
                null,
                responseServiceUuids,
                null,
                null
        );
        Log.d("BLE_TEST", "Current 'response' packet size: " + responsePacketSize + " bytes");
        if (responsePacketSize > 31) {
            Log.e("BLE_TEST", "WARNING: 'response' packet is TOO LARGE!");
        }
        Log.d("BLE_TEST", "--- End of New Calculation Tests ---");
    }

    /**
     * 精確預先計算 AdvertiseData 封包的最終大小 (已重構，符合單一職責原則)。
     * 這個函式模擬 AdvertiseData.Builder 的打包行為，只負責純粹的計算。
     *
     * @param deviceName        要包含的裝置名稱。如果為 null 或 includeDeviceName 為 false，則不計算。
     * @param includeDeviceName 是否包含裝置名稱。
     * @param includeTxPower    是否包含發射功率。
     * @param manufacturerId    廠商 ID，如果為 -1 則不加入。
     * @param manufacturerData  廠商特定資料。
     * @param serviceUuids      要廣播的服務 UUID 列表。
     * @param serviceDataUuid   用於 Service Data 的 UUID。
     * @param serviceData       要附加的 Service Data。
     * @return 計算出的封包大小（位元組）。
     */
    private int calculateAdvertiseDataSize(
            String deviceName, // << 新增參數
            boolean includeDeviceName,
            boolean includeTxPower,
            int manufacturerId,
            byte[] manufacturerData,
            List<ParcelUuid> serviceUuids,
            ParcelUuid serviceDataUuid,
            byte[] serviceData) {

        // 1. Flags: Android 系統總是會加入 Flags AD 結構。
        int size = 3; // 1 (Length) + 1 (Type 0x01) + 1 (Data)

        // 2. 裝置名稱 (AD Type 0x09 - Complete Local Name)
        // *** 核心修正點: 不再呼叫 bluetoothadapter.getName()，而是使用傳入的參數 ***
        if (includeDeviceName && deviceName != null && !deviceName.isEmpty()) {
            size += (2 + deviceName.getBytes(Charset.defaultCharset()).length);
        }

        // 3. 發射功率 (AD Type 0x0A - Tx Power Level)
        if (includeTxPower) {
            size += 3; // 1 (Length) + 1 (Type) + 1 (Data)
        }

        // 4. 廠商特定資料 (AD Type 0xFF - Manufacturer Specific Data)
        if (manufacturerId != -1 && manufacturerData != null) {
            size += (2 + 2 + manufacturerData.length);
        }

        // 5. Service UUID 列表 (AD Types 0x03, 0x07)
        if (serviceUuids != null && !serviceUuids.isEmpty()) {
            List<ParcelUuid> uuids16 = new ArrayList<>();
            List<ParcelUuid> uuids128 = new ArrayList<>();
            for (ParcelUuid uuid : serviceUuids) {
                if (is16BitUuid(uuid)) {
                    uuids16.add(uuid);
                } else {
                    uuids128.add(uuid);
                }
            }
            if (!uuids16.isEmpty()) {
                size += (2 + uuids16.size() * 2);
            }
            if (!uuids128.isEmpty()) {
                size += (2 + uuids128.size() * 16);
            }
        }

        // 6. Service Data
        if (serviceDataUuid != null && serviceData != null) {
            int dataLen = serviceData.length;
            if (is16BitUuid(serviceDataUuid)) {
                size += (2 + 2 + dataLen);
            } else {
                size += (2 + 16 + dataLen);
            }
        }
        return size;
    }

    /**
     * 輔助函式，檢查一個 ParcelUuid 是否為 16-bit 標準 UUID。
     * 標準 UUID 的格式為 0000xxxx-0000-1000-8000-00805f9b34fb。
     */
    private boolean is16BitUuid(ParcelUuid parcelUuid) {
        UUID uuid = parcelUuid.getUuid();
        // 比較最高 64 位和最低 64 位是否符合藍牙基礎 UUID
        return (uuid.getMostSignificantBits() & 0xFFFF0000FFFFFFFFL) == 0x0000000000001000L &&
                uuid.getLeastSignificantBits() == 0x800000805f9b34fbL;
    }
}
