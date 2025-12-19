package com.tutsplus.bleadvertising;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
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
import android.os.ParcelUuid;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.tutsplus.bleadvertising.databinding.ActivityMainBinding;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private ActivityMainBinding binding;
    private BleViewModel bleViewModel;
    private BluetoothManager bluetoothManager; // 負責管理藍牙
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private BluetoothLeAdvertiser bleAdvertiser;

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissionsResult);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        bleViewModel = new ViewModelProvider(this).get(BleViewModel.class);
        if (!initBluetooth()) {
            disableAllButtons();
            return;
        }
        binding.discoverBtn.setOnClickListener(v -> {
            BleState currentState = bleViewModel.bleState.getValue();
            if (currentState == BleState.SCANNING) {
                bleViewModel.updateState(BleState.IDLE);
            } else if (currentState == BleState.IDLE) {
                bleViewModel.updateState(BleState.START_SCAN);
            }
        });

        binding.advertiseBtn.setOnClickListener(v -> {
            BleState currentState = bleViewModel.bleState.getValue();
            if (currentState == BleState.ADVERTISING) {
                bleViewModel.updateState(BleState.IDLE);
            } else if (currentState == BleState.IDLE) {
                bleViewModel.updateState(BleState.START_ADVERTISE);
            }
        });

        bleViewModel.bleState.observe(this, new Observer<BleState>() {
            @Override
            public void onChanged(@NonNull BleState state) {
                Log.d(TAG, "State changed to: " + state);
                switch (state) {
                    case IDLE:
                        stopBleScan();
                        stopBleAdvertising();
                        updateUiForIdleState();
                        break;
                    case START_SCAN:
                        stopBleAdvertising();
                        updateUiForScanningState();
                        startBleScan();
                        break;
                    case SCANNING:
                        Toast.makeText(getBaseContext(), "掃描已開始", Toast.LENGTH_SHORT).show();
                        break;
                    case START_ADVERTISE:
                        stopBleScan();
                        updateUiForAdvertisingState();
                        startBleAdvertising();
                        break;
                    case ADVERTISING:
                        Toast.makeText(getBaseContext(), "廣播已開始", Toast.LENGTH_SHORT).show();
                        break;
                    case SCAN_STOPPING:
                        bleViewModel.updateState(BleState.IDLE);
                        break;
                    case FAILURE:
                        stopBleScan();
                        stopBleAdvertising();
                        updateUiForIdleState();
                        break;
                }
            }
        });

        bleViewModel.scanResultText.observe(this, text -> {
            if (bleViewModel.bleState.getValue() == BleState.SCANNING && !TextUtils.isEmpty(text)) {
                binding.text.setText(text);
            }
        });

        bleViewModel.toastMessage.observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean initBluetooth() {
        bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            Toast.makeText(this, "此裝置不支援藍牙", Toast.LENGTH_LONG).show();
            return false;
        }
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "無法取得 BluetoothAdapter", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private void startBleScan() {
        if (!areScanPermissionsGranted()) {
            bleViewModel.postToastMessage("缺少藍牙掃描權限");
            bleViewModel.updateState(BleState.FAILURE);
            checkAndRequestPermissions();
            return;
        }
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bleScanner == null) {
            bleViewModel.postToastMessage("無法取得 BLE Scanner");
            bleViewModel.updateState(BleState.FAILURE);
            return;
        }
        final List<ScanFilter> filters = new ArrayList<>();
        try {
            final ParcelUuid serviceUuid = new ParcelUuid(UUID.fromString(getString(R.string.ble_uuid)));
            filters.add(new ScanFilter.Builder().setServiceUuid(serviceUuid).build());
        } catch (Exception e) {
            bleViewModel.postToastMessage("無效的 UUID 字串 (用於掃描過濾)");
            bleViewModel.updateState(BleState.FAILURE);
            return;
        }
        final ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        try {
            bleScanner.startScan(filters, settings, scanCallback);
            Log.i(TAG, "BLE scan started.");
            bleViewModel.updateState(BleState.SCANNING);
        } catch (SecurityException e) {
            Log.e(TAG, "無法開始掃描，缺少權限", e);
            bleViewModel.postToastMessage("缺少權限，無法開始掃描");
            bleViewModel.updateState(BleState.FAILURE);
        }
    }

    private void stopBleScan() {
        if (bleScanner != null && bluetoothAdapter.isEnabled() && areScanPermissionsGranted()) {
            try {
                bleScanner.stopScan(scanCallback);
                Log.i(TAG, "BLE scan stopped.");
            } catch (SecurityException e) {
                Log.e(TAG, "無法停止掃描，缺少權限", e);
            }
        }
        bleScanner = null;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void startBleAdvertising() {
        if (!areAdvertisePermissionsGranted()) {
            bleViewModel.postToastMessage("缺少藍牙廣播權限");
            bleViewModel.updateState(BleState.FAILURE);
            checkAndRequestPermissions();
            return;
        }
        final BluetoothGattService batteryService = createBatteryService();
        final BluetoothGattService heartRateService = createHeartRateService(); // 建立心率服務
        final BluetoothGattService healthThermometerService = createHealthThermometerService(); // 建立溫度計服務
        final BluetoothGattService deviceInfoService = createDeviceInfoService();
        final BluetoothGattService currentTimeService = createCurrentTimeService();

        if (areConnectPermissionsGranted()) {
            try {
                final BluetoothGattServer gattServer = bluetoothManager.openGattServer(this, bleViewModel.getGattServerCallback());
                if (gattServer == null) {
                    Log.e(TAG, "無法開啟 GATT 伺服器");
                } else {
                    bleViewModel.setupGattServerLogic(gattServer);

                    bleViewModel.addGattService(batteryService);
                    bleViewModel.addGattService(heartRateService);
                    bleViewModel.addGattService(healthThermometerService);
                    bleViewModel.addGattService(deviceInfoService);
                    bleViewModel.addGattService(currentTimeService);

                }
            } catch (SecurityException e) {
                Log.e(TAG, "建立 GATT 伺服器失敗，缺少權限", e);
                bleViewModel.postToastMessage("建立 GATT 伺服器失敗");
            }
        }

        bleAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (bleAdvertiser == null || !bluetoothAdapter.isMultipleAdvertisementSupported()) {
            bleViewModel.postToastMessage("此裝置不支援 BLE 廣播或多重廣播");
            bleViewModel.updateState(BleState.FAILURE);
            return;
        }

        try {
            final AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(true)
                    .build();

            final int appleCompanyId = 0x004C;
            final byte[] manufacturerDataBytes = new byte[]{0x12, 0x02, 0x00, 0x02};
            final byte[] manufacturerDataiBeaconBytes = new byte[25];
            // 2. 寫入固定的 Beacon Type (2 bytes)
            manufacturerDataiBeaconBytes[0] = 0x02; // Proximity Beacon type
            manufacturerDataiBeaconBytes[1] = 0x15; // Data length (21 bytes)

            // 3. 寫入你的 Proximity UUID (16 bytes)
            // 範例 UUID: "E2C56DB5-DFFB-48D2-B060-D0F5A71096E0"
            // 你需要一個輔助函式將 UUID 字串轉換為 byte 陣列
            UUID proximityUuid = UUID.fromString("E2C56DB5-DFFB-48D2-B060-D0F5A71096E0");
            System.arraycopy(asBytes(proximityUuid), 0, manufacturerDataiBeaconBytes, 2, 16);

            // 4. 寫入 Major (2 bytes) - 範例值: 10001 (0x2711)
            int major = 10001;
            manufacturerDataiBeaconBytes[18] = (byte) (major >> 8);
            manufacturerDataiBeaconBytes[19] = (byte) major;

            // 5. 寫入 Minor (2 bytes) - 範例值: 20002 (0x4E22)
            int minor = 20002;
            manufacturerDataiBeaconBytes[20] = (byte) (minor >> 8);
            manufacturerDataiBeaconBytes[21] = (byte) minor;

            // 6. 寫入 Measured Power (1 byte)
            // 範例值: -59 dBm
            byte txPower = (byte) -59;
            manufacturerDataiBeaconBytes[22] = txPower;

            // 為了補滿25 bytes, Apple 有時候會加入一些保留位元
            // 但實際上從 [0] 到 [22] 已經是完整的 iBeacon 格式 (共23 bytes)
            // 這裡我們只傳送這 23 bytes 的有效資料
            byte[] iBeaconData = new byte[23];
            System.arraycopy(manufacturerDataiBeaconBytes, 0, iBeaconData, 0, 23);


            final ParcelUuid amsServiceUuid = new ParcelUuid(UUID.fromString("89D3502B-0F36-433A-8EF4-C502AD55F8DC"));
            final ParcelUuid serviceDataUuid = new ParcelUuid(UUID.fromString("0000FEA0-0000-1000-8000-00805f9b34fb"));
            final UUID BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb");
            final ParcelUuid batteryServiceUuid = new ParcelUuid(BATTERY_SERVICE_UUID);
            final ParcelUuid heartRateServiceUuid = new ParcelUuid(heartRateService.getUuid());
            final ParcelUuid healthThermometerServiceUuid = new ParcelUuid(healthThermometerService.getUuid());

            final ParcelUuid deviceInfoServiceUuid = new ParcelUuid(UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb"));
            final ParcelUuid currentTimeServiceUuid = new ParcelUuid(UUID.fromString("00001805-0000-1000-8000-00805f9b34fb"));
            final byte[] serviceDataBytes = new byte[]{0x01};
            boolean includeDeviceNameInData = false;
            String deviceName = null;

            if (includeDeviceNameInData) {
                try {
                    deviceName = bluetoothAdapter.getName();
                    if (TextUtils.isEmpty(deviceName)) {
                        includeDeviceNameInData = false;
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "無法獲取裝置名稱，缺少 BLUETOOTH_CONNECT 權限", e);
                    bleViewModel.postToastMessage("權限不足，無法廣播裝置名稱");
                    // 強制不包含名稱
                    includeDeviceNameInData = false;
                }
            }
            final AdvertiseData data = new AdvertiseData.Builder()
                    .setIncludeDeviceName(includeDeviceNameInData)
                    .setIncludeTxPowerLevel(false)
                    .addManufacturerData(appleCompanyId, manufacturerDataBytes)
                    .addServiceData(serviceDataUuid, serviceDataBytes)
//                    .addServiceUuid(batteryServiceUuid)
//                    .addServiceUuid(heartRateServiceUuid)
//                    .addServiceUuid(healthThermometerServiceUuid)
//                    .addServiceUuid(deviceInfoServiceUuid)
//                    .addServiceUuid(currentTimeServiceUuid)
                    .build();

            final AdvertiseData iBeaconAdvertiseData = new AdvertiseData.Builder()
                    .setIncludeDeviceName(false) // iBeacon 不包含裝置名稱
                    .setIncludeTxPowerLevel(false) // Tx Power 已包含在 manufacturer data 中
                    .addManufacturerData(appleCompanyId, iBeaconData)
                    .build();


            final AdvertiseData response = new AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .addServiceUuid(amsServiceUuid)
                    .build();

            final AdvertiseData iBeaconAdvertiseresponse = new AdvertiseData.Builder().build();


            Log.d(TAG, "--- STARTING CALCULATION COMPARISON ---");

            // 1. 呼叫您原始的、正確的多參數計算函式 (作為基準)
            int dataPacketSize_Old = calculateAdvertiseDataSize(
                    null, // deviceName
                    data.getIncludeDeviceName(),
                    data.getIncludeTxPowerLevel(),
                    appleCompanyId,
                    manufacturerDataBytes,
                    data.getServiceUuids(),
                    serviceDataUuid,
                    serviceDataBytes
            );
            int responsePacketSize_Old = calculateAdvertiseDataSize(
                    null, // deviceName
                    response.getIncludeDeviceName(),
                    response.getIncludeTxPowerLevel(),
                    -1, // No manufacturer data in response
                    null,
                    response.getServiceUuids(),
                    null, // No service data in response
                    null
            );
            Log.d(TAG, "[COMPARISON] OLD (Correct) 'data' size: " + dataPacketSize_Old + " bytes");
            Log.d(TAG, "[COMPARISON] OLD (Correct) 'response' size: " + responsePacketSize_Old + " bytes");

            // 2. 呼叫新的、基於 AdvertiseData 物件的計算函式
            int dataPacketSize_New = calculateAdvertiseDataSizeNew(data, deviceName);
            int responsePacketSize_New = calculateAdvertiseDataSizeNew(response, null); //
            Log.d(TAG, "[COMPARISON] NEW (To be verified) 'data' size: " + dataPacketSize_New + " bytes");
            Log.d(TAG, "[COMPARISON] NEW (To be verified) 'response' size: " + responsePacketSize_New + " bytes");
            Log.d(TAG, "--- FINISHED CALCULATION COMPARISON ---");

            // 最終使用經過驗證的舊函式結果來執行檢查
            if (dataPacketSize_Old > 31) {
                bleViewModel.postToastMessage("廣播資料 (data) 超過31位元組限制: " + dataPacketSize_Old);
                bleViewModel.updateState(BleState.FAILURE);
                return;
            }
            if (responsePacketSize_Old > 31) {
                bleViewModel.postToastMessage("掃描回應 (response) 超過31位元組限制: " + responsePacketSize_Old);
                bleViewModel.updateState(BleState.FAILURE);
                return;
            }

            // 開始廣播
            bleAdvertiser.startAdvertising(settings, data, response, advertisingCallback);

            Log.i(TAG, "BLE advertising started with complex data and scan response.");

        } catch (Exception e) {
            Log.e(TAG, "建立或開始廣播時發生錯誤", e);
            bleViewModel.postToastMessage("廣播失敗: " + e.getMessage());
            bleViewModel.updateState(BleState.FAILURE);
        }
    }

    private void stopBleAdvertising() {
        if (bleAdvertiser != null && bluetoothAdapter.isEnabled() && areAdvertisePermissionsGranted()) {
            try {
                bleAdvertiser.stopAdvertising(advertisingCallback);
                Log.i(TAG, "BLE advertising stopped.");
            } catch (SecurityException e) {
                Log.e(TAG, "無法停止廣播，缺少權限", e);
            }
        }
        bleAdvertiser = null;
    }


    private BluetoothGattService createBatteryService() {
        final UUID BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb");
        final UUID BATTERY_LEVEL_UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb");
        final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        final UUID CHARACTERISTIC_USER_DESCRIPTION_UUID = UUID.fromString("00002901-0000-1000-8000-00805f9b34fb");
        final UUID CLIENT_CHARACTERISTIC_CONFIGURATION_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        final String BATTERY_LEVEL_DESCRIPTION = "The current charge level of a battery. 100% represents fully charged while 0% represents fully discharged.";
        // 1. 建立特徵
        BluetoothGattCharacteristic batteryLevelCharacteristic = new BluetoothGattCharacteristic(
                BATTERY_LEVEL_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
        );
        batteryLevelCharacteristic.setValue(new byte[]{(byte) 80}); // 初始電量 80%

        // 2. 建立 CCCD 描述符並加入特徵
        BluetoothGattDescriptor cccDescriptor = new BluetoothGattDescriptor(
                CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE
        );
        cccDescriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        batteryLevelCharacteristic.addDescriptor(cccDescriptor);

        // 可選: 加入用戶描述
        // final UUID USER_DESC_UUID = UUID.fromString("00002901-0000-1000-8000-00805f9b34fb");
        // BluetoothGattDescriptor userDescriptor = new BluetoothGattDescriptor(USER_DESC_UUID, BluetoothGattDescriptor.PERMISSION_READ);
        // try {
        //     userDescriptor.setValue(BATTERY_LEVEL_DESCRIPTION.getBytes("UTF-8"));
        //     batteryLevelCharacteristic.addDescriptor(userDescriptor);
        // } catch (UnsupportedEncodingException e) {
        //     Log.e(TAG, "無法設定用戶描述", e);
        // }

        // 3. 建立服務並加入特徵
        BluetoothGattService batteryService = new BluetoothGattService(BATTERY_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        batteryService.addCharacteristic(batteryLevelCharacteristic);

        return batteryService;
    }

    private BluetoothGattService createHeartRateService() {
        final UUID HEART_RATE_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb");
        final UUID HEART_RATE_MEASUREMENT_UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb");
        final UUID BODY_SENSOR_LOCATION_UUID = UUID.fromString("00002A38-0000-1000-8000-00805f9b34fb");
        final UUID HEART_RATE_CONTROL_POINT_UUID = UUID.fromString("00002A39-0000-1000-8000-00805f9b34fb");
        final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

        // 建立服務
        BluetoothGattService heartRateService = new BluetoothGattService(HEART_RATE_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        // 1. 心率測量特徵 (Heart Rate Measurement Characteristic)
        BluetoothGattCharacteristic heartRateMeasurementCharacteristic = new BluetoothGattCharacteristic(
                HEART_RATE_MEASUREMENT_UUID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                0 // 無權限，因為它是通知
        );
        // 加入 CCCD 以啟用通知
        heartRateMeasurementCharacteristic.addDescriptor(new BluetoothGattDescriptor(CCCD_UUID, BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));
        // 設定初始值 (Flags: UINT8, Sensor Not Connected, Energy Expended Present)
        heartRateMeasurementCharacteristic.setValue(new byte[]{0b00001000, 60, 0, 0}); // 初始心率 60, 能量 0
        heartRateService.addCharacteristic(heartRateMeasurementCharacteristic);

        // 2. 身體感測器位置特徵 (Body Sensor Location Characteristic)
        BluetoothGattCharacteristic bodySensorLocationCharacteristic = new BluetoothGattCharacteristic(
                BODY_SENSOR_LOCATION_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );
        bodySensorLocationCharacteristic.setValue(new byte[]{0x01}); // 初始位置: 胸部 (Chest)
        heartRateService.addCharacteristic(bodySensorLocationCharacteristic);

        // 3. 心率控制點特徵 (Heart Rate Control Point Characteristic)
        BluetoothGattCharacteristic heartRateControlPointCharacteristic = new BluetoothGattCharacteristic(
                HEART_RATE_CONTROL_POINT_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
        );
        heartRateService.addCharacteristic(heartRateControlPointCharacteristic);

        return heartRateService;
    }

    private BluetoothGattService createHealthThermometerService() {
        final UUID HEALTH_THERMOMETER_SERVICE_UUID = UUID.fromString("00001809-0000-1000-8000-00805f9b34fb");
        final UUID TEMPERATURE_MEASUREMENT_UUID = UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb");
        final UUID MEASUREMENT_INTERVAL_UUID = UUID.fromString("00002A21-0000-1000-8000-00805f9b34fb"); // 新增：測量間隔 UUID
        final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

        // 建立服務
        BluetoothGattService healthThermometerService = new BluetoothGattService(HEALTH_THERMOMETER_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        // 1. 溫度測量特徵 (Temperature Measurement Characteristic)
        BluetoothGattCharacteristic temperatureMeasurementCharacteristic = new BluetoothGattCharacteristic(
                TEMPERATURE_MEASUREMENT_UUID,
                BluetoothGattCharacteristic.PROPERTY_INDICATE, // 注意：這是 Indicate (需要確認)
                0 // 無權限
        );
        // 加入 CCCD
        temperatureMeasurementCharacteristic.addDescriptor(new BluetoothGattDescriptor(CCCD_UUID, BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));

        // 設定初始溫度值 (37.0f)
        float initialTemp = 37.0f;
        int bits = Float.floatToIntBits(initialTemp);
        temperatureMeasurementCharacteristic.setValue(new byte[5]); // Flags + float
        temperatureMeasurementCharacteristic.setValue(bits, BluetoothGattCharacteristic.FORMAT_FLOAT, 1);
        healthThermometerService.addCharacteristic(temperatureMeasurementCharacteristic);

        // --- 新增開始 ---
        // 2. 測量間隔特徵 (Measurement Interval Characteristic)
        // 根據SIG標準，這個特徵是可選的，屬性為可讀(Read)，可選可寫(Optional Write)。
        // 它的值是一個 uint16，單位是秒。
        BluetoothGattCharacteristic measurementIntervalCharacteristic = new BluetoothGattCharacteristic(
                MEASUREMENT_INTERVAL_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE, // 設為可讀可寫
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE
        );

        // 設定一個初始的測量間隔，例如 5 秒
        int interval = 5; // 5 seconds
        measurementIntervalCharacteristic.setValue(new byte[2]);
        measurementIntervalCharacteristic.setValue(interval, BluetoothGattCharacteristic.FORMAT_UINT16, 0);

        // 根據HealthThermometerServiceFragment.java，它還有一個 "Valid Range" 描述符
        // 這個描述符 (UUID: 0x2906) 用來告訴客戶端可接受的寫入範圍。
        final UUID VALID_RANGE_DESCRIPTOR_UUID = UUID.fromString("00002906-0000-1000-8000-00805f9b34fb");
        BluetoothGattDescriptor validRangeDescriptor = new BluetoothGattDescriptor(
                VALID_RANGE_DESCRIPTOR_UUID,
                BluetoothGattDescriptor.PERMISSION_READ // 該描述符為唯讀
        );
        // 設定有效範圍，例如 1 到 60 秒
        int lowerBound = 1;
        int upperBound = 60;
        byte[] range = new byte[4]; // 2 bytes for lower, 2 bytes for upper
        range[0] = (byte) (lowerBound & 0xFF);
        range[1] = (byte) ((lowerBound >> 8) & 0xFF);
        range[2] = (byte) (upperBound & 0xFF);
        range[3] = (byte) ((upperBound >> 8) & 0xFF);
        validRangeDescriptor.setValue(range);
        measurementIntervalCharacteristic.addDescriptor(validRangeDescriptor);

        healthThermometerService.addCharacteristic(measurementIntervalCharacteristic);

        return healthThermometerService;
    }

    private BluetoothGattService createDeviceInfoService() {
        final UUID DEVICE_INFO_SERVICE_UUID = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb");
        final BluetoothGattService deviceInfoService = new BluetoothGattService(DEVICE_INFO_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        // --- 特徵: Manufacturer Name String (UUID: 0x2A29) ---
        final UUID MANUFACTURER_NAME_UUID = UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb");
        final BluetoothGattCharacteristic manufacturerNameChar = new BluetoothGattCharacteristic(
                MANUFACTURER_NAME_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );
        manufacturerNameChar.setValue("MyAndroidDevice");
        deviceInfoService.addCharacteristic(manufacturerNameChar);

        // --- 特徵: Model Number String (UUID: 0x2A24) ---
        final UUID MODEL_NUMBER_UUID = UUID.fromString("00002A24-0000-1000-8000-00805f9b34fb");
        final BluetoothGattCharacteristic modelNumberChar = new BluetoothGattCharacteristic(
                MODEL_NUMBER_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );
        modelNumberChar.setValue(Build.MODEL);
        deviceInfoService.addCharacteristic(modelNumberChar);

        // --- 特徵: Serial Number String (UUID: 0x2A25) ---
        final UUID SERIAL_NUMBER_UUID = UUID.fromString("00002A25-0000-1000-8000-00805f9b34fb");
        final BluetoothGattCharacteristic serialNumberChar = new BluetoothGattCharacteristic(
                SERIAL_NUMBER_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );
        serialNumberChar.setValue("1234-ABCD");
        deviceInfoService.addCharacteristic(serialNumberChar);

        return deviceInfoService;
    }

    /**
     * 依據 SIG 標準建立當前時間服務 (Current Time Service)
     * Service UUID: 0x1805
     */
    private BluetoothGattService createCurrentTimeService() {
        final UUID CURRENT_TIME_SERVICE_UUID = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb");
        final BluetoothGattService currentTimeService = new BluetoothGattService(CURRENT_TIME_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        // --- 特徵: Current Time (UUID: 0x2A2B) ---
        final UUID CURRENT_TIME_UUID = UUID.fromString("00002A2B-0000-1000-8000-00805f9b34fb");
        final BluetoothGattCharacteristic currentTimeChar = new BluetoothGattCharacteristic(
                CURRENT_TIME_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
        );

        final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        final BluetoothGattDescriptor cccDescriptor = new BluetoothGattDescriptor(
                CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE
        );
        cccDescriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        currentTimeChar.addDescriptor(cccDescriptor);

        // 動態獲取當前時間並設定
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1; // Month is 0-based
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK); // Sunday=1, ..., Saturday=7
        if (dayOfWeek == Calendar.SUNDAY) { dayOfWeek = 7; } else { dayOfWeek -= 1; } // SIG: Monday=1..Sunday=7

        byte[] timeValue = new byte[10];
        timeValue[0] = (byte) (year & 0xFF);
        timeValue[1] = (byte) ((year >> 8) & 0xFF);
        timeValue[2] = (byte) month;
        timeValue[3] = (byte) day;
        timeValue[4] = (byte) hour;
        timeValue[5] = (byte) minute;
        timeValue[6] = (byte) second;
        timeValue[7] = (byte) dayOfWeek;
        timeValue[8] = 0; // Fractions256
        timeValue[9] = 1; // Adjust Reason (Manual update)
        currentTimeChar.setValue(timeValue);

        currentTimeService.addCharacteristic(currentTimeChar);

        return currentTimeService;
    }


    private void updateUiForIdleState() {
        binding.text.setText("閒置中");
        binding.discoverBtn.setText("開始掃描");
        binding.advertiseBtn.setText("開始廣播");
        binding.discoverBtn.setEnabled(areScanPermissionsGranted());
        binding.advertiseBtn.setEnabled(areAdvertisePermissionsGranted() && bluetoothAdapter.isMultipleAdvertisementSupported());
    }

    private void updateUiForScanningState() {
        binding.text.setText("正在掃描...");
        binding.discoverBtn.setText("停止掃描");
        binding.advertiseBtn.setEnabled(false);
        binding.discoverBtn.setEnabled(true);
    }

    private void updateUiForAdvertisingState() {
        binding.text.setText("正在廣播...");
        binding.advertiseBtn.setText("停止廣播");
        binding.discoverBtn.setEnabled(false);
        binding.advertiseBtn.setEnabled(true);
    }

    private void disableAllButtons() {
        binding.discoverBtn.setEnabled(false);
        binding.advertiseBtn.setEnabled(false);
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.e("MainActivity", "onScanResult : " + result);
            if (result == null || result.getDevice() == null) return;
            String deviceName;
            try {
                deviceName = result.getDevice().getName();
            } catch (SecurityException e) {
                deviceName = "Unnamed Device";
            }
            if (TextUtils.isEmpty(deviceName)) return;

            final StringBuilder builder = new StringBuilder(deviceName);
            if (result.getScanRecord() != null && result.getScanRecord().getServiceUuids() != null && !result.getScanRecord().getServiceUuids().isEmpty()) {
                ParcelUuid pUuid = result.getScanRecord().getServiceUuids().get(0);
                if (result.getScanRecord().getServiceData(pUuid) != null) {
                    builder.append("\n").append(new String(result.getScanRecord().getServiceData(pUuid), StandardCharsets.UTF_8));
                }
            }
            bleViewModel.setScanResultText(builder.toString());
        }

        @Override
        public void onScanFailed(int errorCode) {
            bleViewModel.postToastMessage("掃描失敗: " + errorCode);
            bleViewModel.updateState(BleState.FAILURE);
        }
    };

    private final AdvertiseCallback advertisingCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            bleViewModel.updateState(BleState.ADVERTISING);
            Log.i(TAG, "Advertising onStartSuccess." + settingsInEffect);
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            String errorText = getAdvertiseError(errorCode);
            Log.e(TAG, "Advertising onStartFailure: " + errorText);
            bleViewModel.postToastMessage("廣播失敗: " + errorText);
            bleViewModel.updateState(BleState.FAILURE);
        }
    };

    // ***********************************************************************************
    // --- 【廣播資料大小計算輔助函式】---
    // ***********************************************************************************

    /**
     * 【關鍵修正】恢復您原始的、能夠正確識別 SIG 16-bit UUID 的函式。
     */
    private boolean is16BitUuid(UUID uuid) {
        return (uuid.getMostSignificantBits() & 0xFFFF0000FFFFFFFFL) == 0x0000000000001000L &&
                uuid.getLeastSignificantBits() == 0x800000805f9b34fbL;
    }

    /**
     * 【關鍵修正】完整恢復您原始的、邏輯正確的多參數計算函式 (作為比較的基準)。
     */
    private int calculateAdvertiseDataSize(
            String deviceName,
            boolean includeDeviceName,
            boolean includeTxPower,
            int manufacturerId,
            byte[] manufacturerData,
            List<ParcelUuid> serviceUuids,
            ParcelUuid serviceDataUuid,
            byte[] serviceData) {

        int size = 3; // Flags

        if (includeDeviceName && deviceName != null && !deviceName.isEmpty()) {
            size += (2 + deviceName.getBytes(StandardCharsets.UTF_8).length);
        }

        if (includeTxPower) {
            size += 3;
        }

        if (manufacturerId != -1 && manufacturerData != null) {
            size += (2 + 2 + manufacturerData.length);
        }

        if (serviceUuids != null && !serviceUuids.isEmpty()) {
            List<ParcelUuid> uuids16 = new ArrayList<>();
            List<ParcelUuid> uuids128 = new ArrayList<>();
            for (ParcelUuid uuid : serviceUuids) {
                if (is16BitUuid(uuid.getUuid())) {
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

        if (serviceDataUuid != null && serviceData != null) {
            int dataLen = serviceData.length;
            if (is16BitUuid(serviceDataUuid.getUuid())) {
                size += (2 + 2 + dataLen);
            } else {
                size += (2 + 16 + dataLen);
            }
        }
        return size;
    }

    /**
     * 【新增】新的、基於 AdvertiseData 物件的計算函式，用於偵錯和比對。
     * 它會從 AdvertiseData 物件中提取資訊，並使用與舊函式完全相同的邏輯進行計算。
     */
    private int calculateAdvertiseDataSizeNew(AdvertiseData data, @Nullable String deviceName) {
        if (data == null) return 0;

        // 從 AdvertiseData 物件中提取參數
        boolean includeDeviceName = data.getIncludeDeviceName();
        boolean includeTxPower = data.getIncludeTxPowerLevel();
        List<ParcelUuid> serviceUuids = data.getServiceUuids();

        // 提取 Manufacturer Data
        int manufacturerId = -1;
        byte[] manufacturerDataBytes = null;
        if (data.getManufacturerSpecificData() != null && data.getManufacturerSpecificData().size() > 0) {
            // 假設只處理第一個 Manufacturer Data
            manufacturerId = data.getManufacturerSpecificData().keyAt(0);
            manufacturerDataBytes = data.getManufacturerSpecificData().valueAt(0);
        }

        // 提取 Service Data
        ParcelUuid serviceDataUuid = null;
        byte[] serviceDataBytes = null;
        if (data.getServiceData() != null && !data.getServiceData().isEmpty()) {
            // 假設只處理第一個 Service Data
            Map.Entry<ParcelUuid, byte[]> entry = data.getServiceData().entrySet().iterator().next();
            serviceDataUuid = entry.getKey();
            serviceDataBytes = entry.getValue();
        }

        // 呼叫原始的計算函式
        return calculateAdvertiseDataSize(
                deviceName,
                includeDeviceName,
                includeTxPower,
                manufacturerId,
                manufacturerDataBytes,
                serviceUuids,
                serviceDataUuid,
                serviceDataBytes
        );
    }


    // --- 權限管理 ---
    private void onPermissionsResult(Map<String, Boolean> result) {
        // 重新檢查我們關心的掃描和廣播權限是否已獲取
        boolean scanGranted = areScanPermissionsGranted();
        boolean advertiseGranted = areAdvertisePermissionsGranted();

        if (scanGranted && advertiseGranted) {
            Log.d(TAG, "所有必要的藍牙權限均已授予。");
            // 如果使用者剛授予權限，可以根據當前狀態決定是否自動開始操作
            BleState currentState = bleViewModel.bleState.getValue();
            if (currentState == BleState.START_SCAN) {
                startBleScan();
            } else if (currentState == BleState.START_ADVERTISE) {
                startBleAdvertising();
            }
        } else {
            Log.w(TAG, "部分或全部藍牙權限被拒絕。 Scan granted: " + scanGranted + ", Advertise granted: " + advertiseGranted);
            bleViewModel.postToastMessage("權限被拒絕，功能可能受限");
            bleViewModel.updateState(BleState.FAILURE); // 更新為失敗狀態
        }
    }


    private void checkAndRequestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else {
            // Android 11 及以下
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
            // BLUETOOTH 和 BLUETOOTH_ADMIN 是安裝時權限，不需要在執行時請求
        }

        if (!permissionsToRequest.isEmpty()) {
            Log.d(TAG, "正在請求權限: " + permissionsToRequest);
            requestPermissionLauncher.launch(permissionsToRequest.toArray(new String[0]));
        }
    }

    private boolean areScanPermissionsGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 (API 31) 及以上版本需要 BLUETOOTH_SCAN
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 11 (API 30) 及以下版本需要 ACCESS_FINE_LOCATION
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private boolean areAdvertisePermissionsGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 (API 31) 及以上版本需要 BLUETOOTH_ADVERTISE
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED;
        } else {
            // 廣播在舊版中不需要特定權限，始終返回 true
            return true;
        }
    }

    private boolean areConnectPermissionsGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            // Android S 以下版本，連線不需要特別的執行期權限
            return true;
        }
    }

    private String getAdvertiseError(int errorCode) {
        switch (errorCode) {
            case AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED:
                return "ALREADY_STARTED";
            case AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE:
                return "DATA_TOO_LARGE";
            case AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                return "FEATURE_UNSUPPORTED";
            case AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR:
                return "INTERNAL_ERROR";
            case AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                return "TOO_MANY_ADVERTISERS";
            default:
                return "未知錯誤: " + errorCode;
        }
    }

    /**
     * 輔助函式，將 UUID 轉換為 big-endian byte 陣列。
     *
     * @param uuid 要轉換的 UUID
     * @return 16-byte 的陣列
     */
    public static byte[] asBytes(UUID uuid) {
        byte[] bytes = new byte[16];
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (msb >>> (8 * (7 - i)));
        }
        for (int i = 8; i < 16; i++) {
            bytes[i] = (byte) (lsb >>> (8 * (7 - (i - 8))));
        }
        return bytes;
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkAndRequestPermissions();
    }

    @Override
    protected void onStop() {
        super.onStop();
//        if (bleViewModel.bleState.getValue() != BleState.IDLE) {
//            Log.w(TAG, "Force stopping BLE actions in onStop()");
//            bleViewModel.updateState(BleState.IDLE);
//        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy(): 正在關閉 GATT 伺服器...");
//        // Activity 銷毀時，確實關閉其擁有的 GattServer
//        if (gattServer != null) {
//            if (areConnectPermissionsGranted()) {
//                try {
//                    gattServer.close();
//                } catch (SecurityException e) {
//                    Log.e(TAG, "關閉 GATT 伺服器失敗，缺少權限", e);
//                }
//            }
//            gattServer = null;
//        }
    }
}
