package com.tutsplus.bleadvertising;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 一個單例 (Singleton)，負責建立和管理所有的 GATT 服務，並模擬數據變化與發送通知。
 */
public class ServicesManager {
    private static final String TAG = "ServicesManager";
    private static ServicesManager instance;

    // --- GATT Server 和連線裝置的引用 ---
    private BluetoothGattServer gattServer;
    private HashSet<BluetoothDevice> connectedDevices;

    // --- 所有服務和重要特徵的引用 ---
    private final BluetoothGattService batteryService;
    private final BluetoothGattService heartRateService;
    private final BluetoothGattService healthThermometerService;
    private final BluetoothGattService deviceInfoService;
    private final BluetoothGattService currentTimeService;

    private final BluetoothGattCharacteristic batteryLevelCharacteristic;
    private final BluetoothGattCharacteristic heartRateMeasurementCharacteristic;
    private final BluetoothGattCharacteristic temperatureMeasurementCharacteristic;
    private final BluetoothGattCharacteristic currentTimeCharacteristic;

    // --- 數據模擬線程 ---
    private Thread batterySimulatorThread;
    private Thread heartRateSimulatorThread;
    private Thread temperatureSimulatorThread;
    private volatile boolean isSimulating = false;
    private final Random random = new Random();

    // --- 私有建構函式，確保單例 ---
    private ServicesManager() {
        // 在建構時就建立好所有服務
        this.batteryService = createBatteryService();
        this.heartRateService = createHeartRateService();
        this.healthThermometerService = createHealthThermometerService();
        this.deviceInfoService = createDeviceInfoService();
        this.currentTimeService = createCurrentTimeService();

        // 儲存需要動態更新的特徵的引用
        this.batteryLevelCharacteristic = batteryService.getCharacteristic(UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb"));
        this.heartRateMeasurementCharacteristic = heartRateService.getCharacteristic(UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb"));
        this.temperatureMeasurementCharacteristic = healthThermometerService.getCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"));
        this.currentTimeCharacteristic = currentTimeService.getCharacteristic(UUID.fromString("00002A2B-0000-1000-8000-00805f9b34fb"));
    }

    // --- 獲取單例實例的方法 ---
    public static synchronized ServicesManager getInstance() {
        if (instance == null) {
            instance = new ServicesManager();
        }
        return instance;
    }

    // --- 提供給外部獲取所有服務的方法 ---
    public List<BluetoothGattService> getAllServices() {
        List<BluetoothGattService> services = new ArrayList<>();
        services.add(batteryService);
        services.add(heartRateService);
        services.add(healthThermometerService);
        services.add(deviceInfoService);
        services.add(currentTimeService);
        return services;
    }

    /**
     * 從 ViewModel 注入 GattServer 和連線裝置列表的引用
     */
    public void setGattServer(BluetoothGattServer server, HashSet<BluetoothDevice> devices) {
        this.gattServer = server;
        this.connectedDevices = devices;
    }

    /**
     * 開始模擬數據變化
     */
    public void startSimulation() {
        if (isSimulating) {            Log.d(TAG, "模擬已經在運行中。");
            return;
        }
        isSimulating = true;

        // 模擬電池電量變化
        batterySimulatorThread = new Thread(() -> {
            while (isSimulating) {
                int batteryLevel = 20 + random.nextInt(80);
                byte[] value = GattValueBuilder.forBatteryLevel(batteryLevel);
                batteryLevelCharacteristic.setValue(value);
                notifyCharacteristicChanged(batteryLevelCharacteristic, false);

                try {
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        batterySimulatorThread.start();

        // 模擬心率變化
        heartRateSimulatorThread = new Thread(() -> {
            while (isSimulating) {
                int heartRate = 60 + random.nextInt(15);
                byte[] value = GattValueBuilder.forHeartRateMeasurement(heartRate);
                heartRateMeasurementCharacteristic.setValue(value);

                notifyCharacteristicChanged(heartRateMeasurementCharacteristic, false);

                try {
                    Thread.sleep(29000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        heartRateSimulatorThread.start();

        // 模擬溫度變化
        temperatureSimulatorThread = new Thread(() -> {
            while (isSimulating) {
                float temperature = 36.5f + random.nextFloat();
                byte[] value = GattValueBuilder.forTemperatureMeasurement(temperature);
                temperatureMeasurementCharacteristic.setValue(value);
                notifyCharacteristicChanged(temperatureMeasurementCharacteristic, true);

                try {
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        temperatureSimulatorThread.start();

        Log.i(TAG, "數據模擬已開始。");
    }

    /**
     * 停止模擬數據變化
     */
    public void stopSimulation() {
        isSimulating = false;
        if (batterySimulatorThread != null) batterySimulatorThread.interrupt();
        if (heartRateSimulatorThread != null) heartRateSimulatorThread.interrupt();
        if (temperatureSimulatorThread != null) temperatureSimulatorThread.interrupt();
        Log.i(TAG, "數據模擬已停止。");
    }

    /**
     * 輔助方法：發送通知/指示給所有已訂閱的裝置
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void notifyCharacteristicChanged(BluetoothGattCharacteristic characteristic, boolean isIndication) {
        if (gattServer == null || connectedDevices == null || connectedDevices.isEmpty()) {
            return;
        }

        UUID cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        BluetoothGattDescriptor cccd = characteristic.getDescriptor(cccdUuid);
        if (cccd == null) return;

        byte[] valueToCompare = isIndication ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;

        for (BluetoothDevice device : connectedDevices) {
            // 這是一個簡化的檢查，在實際應用中，你需要在 onDescriptorWriteRequest 中為每個裝置儲存其訂閱狀態
            // 這裡我們假設客戶端會正確寫入描述符的值，我們直接讀取它
            if (cccd.getValue() != null && java.util.Arrays.equals(cccd.getValue(), valueToCompare)) {
                try {
                    gattServer.notifyCharacteristicChanged(device, characteristic, isIndication);
                } catch (SecurityException e) {
                    Log.e(TAG, "發送通知失敗，缺少權限", e);
                }
            }
        }
    }


    // --- 所有服務的建立方法都移到這裡 ---

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
}
