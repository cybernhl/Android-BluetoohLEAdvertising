package com.tutsplus.bleadvertising;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
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
    private final BluetoothGattService bloodPressureService; // 新增
    private final BluetoothGattService deviceTimeService; // 新增
    private final BluetoothGattService fitnessMachineService;
    private final BluetoothGattService glucoseService; // 新增

    private final BluetoothGattService hearingAidService; // 新增

    private final BluetoothGattService pulseOximeterService; // 新增
    private final BluetoothGattService cyclingPowerService;
    private final BluetoothGattService environmentalSensingService; // 新增


    private final BluetoothGattService weightScaleService;

    private final BluetoothGattCharacteristic batteryLevelCharacteristic;
    private final BluetoothGattCharacteristic heartRateMeasurementCharacteristic;
    private final BluetoothGattCharacteristic temperatureMeasurementCharacteristic;
    private final BluetoothGattCharacteristic currentTimeCharacteristic;
    private final BluetoothGattCharacteristic bloodPressureMeasurementCharacteristic; // 新增
    private final BluetoothGattCharacteristic deviceTimeCharacteristic;
    private final BluetoothGattCharacteristic pulseOximeterMeasurementCharacteristic; // 新增
    private final BluetoothGattCharacteristic cyclingPowerMeasurementCharacteristic;
    // 為 ESS 新增多個特徵的引用
    private final BluetoothGattCharacteristic essTemperatureCharacteristic;
    private final BluetoothGattCharacteristic essHumidityCharacteristic;
    private final BluetoothGattCharacteristic essPressureCharacteristic;
    private final BluetoothGattCharacteristic essWindChillCharacteristic;
    private final BluetoothGattCharacteristic activePresetIndexCharacteristic; // 新增
    private final BluetoothGattCharacteristic indoorBikeDataCharacteristic; // 新增


    private final BluetoothGattCharacteristic glucoseMeasurementCharacteristic; // 新增

    private final BluetoothGattCharacteristic weightScaleMeasurementCharacteristic; // 新增

    // --- 數據模擬線程 ---
    private Thread batterySimulatorThread;
    private Thread heartRateSimulatorThread;
    private Thread temperatureSimulatorThread;
    private Thread bloodPressureSimulatorThread;

    private Thread deviceTimeSimulatorThread;

    private Thread cyclingPowerSimulatorThread;
    private Thread environmentalSensingSimulatorThread;

    private Thread glucoseSimulatorThread;

    private Thread fitnessMachineSimulatorThread;

    private Thread pulseOximeterSimulatorThread;

    private Thread weightScaleSimulatorThread;
    private volatile boolean isSimulating = false;

    private int glucoseSequence = 0; // 用於血糖測量的序列號
    // --- 新增 FTMS 控制相關的成員變數 ---
    private int targetResistanceLevel = 0; // 客戶端設定的目標阻力
    private final Random random = new Random();

    // --- 私有建構函式，確保單例 ---
    private ServicesManager() {
        // 在建構時就建立好所有服務
        this.batteryService = createBatteryService();
        this.heartRateService = createHeartRateService();
        this.healthThermometerService = createHealthThermometerService();
        this.deviceInfoService = createDeviceInfoService();
        this.currentTimeService = createCurrentTimeService();
        this.bloodPressureService = createBloodPressureService();
        this.deviceTimeService = createDeviceTimeService(); // 新增
        this.hearingAidService = createHearingAidService(); // 新增
        this.cyclingPowerService = createCyclingPowerService();
        this.environmentalSensingService = createEnvironmentalSensingService(); // 新增
        this.fitnessMachineService = createFitnessMachineService(); // 新增

        this.glucoseService = createGlucoseService();
        this.pulseOximeterService = createPulseOximeterService(); // 新增

        this.weightScaleService = createWeightScaleService(); // 新增
        // 儲存需要動態更新的特徵的引用
        this.activePresetIndexCharacteristic = hearingAidService.getCharacteristic(UUID.fromString("00002FDC-0000-1000-8000-00805f9b34fb")); // 新增

        this.batteryLevelCharacteristic = batteryService.getCharacteristic(UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb"));
        this.heartRateMeasurementCharacteristic = heartRateService.getCharacteristic(UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb"));
        this.temperatureMeasurementCharacteristic = healthThermometerService.getCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"));
        this.currentTimeCharacteristic = currentTimeService.getCharacteristic(UUID.fromString("00002A2B-0000-1000-8000-00805f9b34fb"));
        this.bloodPressureMeasurementCharacteristic = bloodPressureService.getCharacteristic(UUID.fromString("00002A35-0000-1000-8000-00805f9b34fb"));
        this.deviceTimeCharacteristic = deviceTimeService.getCharacteristic(UUID.fromString("00002B90-0000-1000-8000-00805f9b34fb"));
        this.glucoseMeasurementCharacteristic = glucoseService.getCharacteristic(UUID.fromString("00002A18-0000-1000-8000-00805f9b34fb")); // 新增
        this.pulseOximeterMeasurementCharacteristic = pulseOximeterService.getCharacteristic(UUID.fromString("00002A5E-0000-1000-8000-00805f9b34fb")); // 新增
        this.cyclingPowerMeasurementCharacteristic = cyclingPowerService.getCharacteristic(UUID.fromString("00002A63-0000-1000-8000-00805f9b34fb"));
        this.essTemperatureCharacteristic = environmentalSensingService.getCharacteristic(UUID.fromString("00002A6E-0000-1000-8000-00805f9b34fb"));
        this.essHumidityCharacteristic = environmentalSensingService.getCharacteristic(UUID.fromString("00002A6F-0000-1000-8000-00805f9b34fb"));
        this.essPressureCharacteristic = environmentalSensingService.getCharacteristic(UUID.fromString("00002A6D-0000-1000-8000-00805f9b34fb"));
        this.essWindChillCharacteristic = environmentalSensingService.getCharacteristic(UUID.fromString("00002A79-0000-1000-8000-00805f9b34fb"));
        this.indoorBikeDataCharacteristic = fitnessMachineService.getCharacteristic(UUID.fromString("00002AD2-0000-1000-8000-00805f9b34fb")); // 新增

        this.weightScaleMeasurementCharacteristic = weightScaleService.getCharacteristic(UUID.fromString("00002A9D-0000-1000-8000-00805f9b34fb")); // 新增
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
        services.add(hearingAidService);
        services.add(bloodPressureService); // 新增
        services.add(deviceTimeService); // 新增
        services.add(glucoseService);
        services.add(cyclingPowerService);
        services.add(environmentalSensingService);
        services.add(pulseOximeterService);
        services.add(weightScaleService); // 新增
        services.add(fitnessMachineService);
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
        if (isSimulating) {
            Log.d(TAG, "模擬已經在運行中。");
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

        // 模擬血壓變化
        bloodPressureSimulatorThread = new Thread(() -> {
            while (isSimulating) {
                float systolic = 110 + random.nextInt(20);
                float diastolic = 70 + random.nextInt(15);
                float map = diastolic + (systolic - diastolic) / 3.0f;
                float pulse = 65 + random.nextInt(10);
                byte[] value = GattValueBuilder.forBloodPressureMeasurement(systolic, diastolic, map, pulse, true);
                bloodPressureMeasurementCharacteristic.setValue(value);
                notifyCharacteristicChanged(bloodPressureMeasurementCharacteristic, true); // 血壓使用Indication

                try {
                    // 血壓測量通常不是連續的，間隔可以長一點
                    Thread.sleep(45000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        bloodPressureSimulatorThread.start();

        deviceTimeSimulatorThread = new Thread(() -> {
            while (isSimulating) {
                byte[] value = GattValueBuilder.forDeviceTime(Calendar.getInstance());
                deviceTimeCharacteristic.setValue(value);
                // Device Time 通常是可讀/可寫，但不一定會通知
                // 如果需要通知，取消下面的註解
                // notifyCharacteristicChanged(deviceTimeCharacteristic, false);

                try {
                    // 每秒更新一次時間
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        deviceTimeSimulatorThread.start();

        glucoseSimulatorThread = new Thread(() -> {
            while (isSimulating) {
                // 模擬飯前血糖值 (80-120 mg/dL)
                float glucoseLevel = 80 + random.nextInt(40);
                byte[] value = GattValueBuilder.forGlucoseMeasurement(glucoseSequence++, glucoseLevel, false, true);
                glucoseMeasurementCharacteristic.setValue(value);
                // 血糖測量使用 Notification
                notifyCharacteristicChanged(glucoseMeasurementCharacteristic, false);

                try {
                    // 血糖測量間隔較長
                    Thread.sleep(65000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        glucoseSimulatorThread.start();

        pulseOximeterSimulatorThread = new Thread(() -> {
            while (isSimulating) {
                // 模擬一個正常的血氧值 (95-99%) 和脈率 (60-90 bpm)
                float spo2 = 95 + random.nextInt(5);
                float pulseRate = 60 + random.nextInt(30);

                byte[] value = GattValueBuilder.forPulseOximeterSpotCheck(spo2, pulseRate, true);
                pulseOximeterMeasurementCharacteristic.setValue(value);
                // 血氧測量使用 Notification
                notifyCharacteristicChanged(pulseOximeterMeasurementCharacteristic, false);

                try {
                    // 血氧測量間隔
                    Thread.sleep(15000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        pulseOximeterSimulatorThread.start();

        weightScaleSimulatorThread = new Thread(() -> {
            while (isSimulating) {
                // 模擬一個 65.0kg 到 66.0kg 之間的體重
                float weight = 65.0f + random.nextFloat();
                byte[] value = GattValueBuilder.forWeightScaleMeasurement(weight);
                weightScaleMeasurementCharacteristic.setValue(value);
                // 體重測量通常使用 Indication
                notifyCharacteristicChanged(weightScaleMeasurementCharacteristic, true);

                try {
                    // 體重測量間隔較長
                    Thread.sleep(55000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        weightScaleSimulatorThread.start(); // 新增

        environmentalSensingSimulatorThread = new Thread(() -> {
            while (isSimulating) {
                // 模擬溫度: 20.0 - 25.0 °C
                float temperature = 20.0f + random.nextFloat() * 5;
                // 模擬濕度: 40.0 - 60.0 %
                float humidity = 40.0f + random.nextFloat() * 20;
                // 模擬氣壓: 1010.0 - 1015.0 hPa
                float pressure = 1010.0f + random.nextFloat() * 5;
                // 模擬風寒: 比實際溫度低 2 度
                int windChill = (int) temperature - 2;

                // 更新特徵值
                essTemperatureCharacteristic.setValue(GattValueBuilder.forTemperature(temperature));
                essHumidityCharacteristic.setValue(GattValueBuilder.forHumidity(humidity));
                essPressureCharacteristic.setValue(GattValueBuilder.forPressure(pressure));
                essWindChillCharacteristic.setValue(GattValueBuilder.forWindChill(windChill));

                // 發送通知 (假設這些特徵都支援 Notify)
                notifyCharacteristicChanged(essTemperatureCharacteristic, false);
                notifyCharacteristicChanged(essHumidityCharacteristic, false);
                notifyCharacteristicChanged(essPressureCharacteristic, false);
                notifyCharacteristicChanged(essWindChillCharacteristic, false);

                try {
                    // 環境數據不需要太頻繁更新
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        environmentalSensingSimulatorThread.start();

        fitnessMachineSimulatorThread = new Thread(() -> {
            int totalDistance = 0;
            while (isSimulating) {
                // 模擬基礎數據
                float speed = 25.0f + (random.nextFloat() * 10); // 25-35 km/h
                float cadence = 85.0f + (random.nextFloat() * 10); // 85-95 rpm
                int heartRate = 120 + random.nextInt(20); // 120-140 bpm
                totalDistance += (int) (speed * 1000 / 3600); // 簡單累加距離 (m)

                // 根據目標阻力簡單計算功率
                int basePower = 150;
                int power = basePower + (targetResistanceLevel * 10) + random.nextInt(10);

                // --- 使用擴充後的 Builder ---
                byte[] value = GattValueBuilder.forIndoorBikeData(speed, cadence, power, heartRate, totalDistance);
                indoorBikeDataCharacteristic.setValue(value);
                notifyCharacteristicChanged(indoorBikeDataCharacteristic, false); // Notify

                try {
                    Thread.sleep(1000); // FTMS 數據通常每秒更新一次
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        fitnessMachineSimulatorThread.start();

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
        if (weightScaleSimulatorThread != null) weightScaleSimulatorThread.interrupt();
        if (glucoseSimulatorThread != null) glucoseSimulatorThread.interrupt();
        if (pulseOximeterSimulatorThread != null) pulseOximeterSimulatorThread.interrupt(); // 新增
        if (cyclingPowerSimulatorThread != null) cyclingPowerSimulatorThread.interrupt();
        if (environmentalSensingSimulatorThread != null) environmentalSensingSimulatorThread.interrupt(); // 新增
        if (fitnessMachineSimulatorThread != null) fitnessMachineSimulatorThread.interrupt(); // 新增

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

    public void handleFitnessMachineControlCommand(byte[] command) {
        if (command == null || command.length == 0) return;

        ByteBuffer buffer = ByteBuffer.wrap(command).order(ByteOrder.LITTLE_ENDIAN);
        byte opCode = buffer.get();

        switch (opCode) {
            case 0x04: // Set Target Resistance Level
                if (buffer.remaining() >= 1) {
                    targetResistanceLevel = buffer.get() & 0xFF; // 讀取 uint8
                    Log.i(TAG, "FTMS: 客戶端設定目標阻力為: " + targetResistanceLevel);
                    // 在這裡，你可以根據 targetResistanceLevel 來調整功率的模擬計算
                }
                break;
            // 在這裡可以新增其他 OpCode 的處理, 如 0x05 (Set Target Power)
            default:
                Log.w(TAG, "FTMS: 收到未處理的控制命令 OpCode: " + opCode);
                break;
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

    private BluetoothGattService createBloodPressureService() {
        final UUID BLOOD_PRESSURE_SERVICE_UUID = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb");
        final BluetoothGattService service = new BluetoothGattService(BLOOD_PRESSURE_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        final UUID BLOOD_PRESSURE_MEASUREMENT_UUID = UUID.fromString("00002A35-0000-1000-8000-00805f9b34fb");
        final UUID BLOOD_PRESSURE_FEATURE_UUID = UUID.fromString("00002A49-0000-1000-8000-00805f9b34fb");
        final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

        // 1. 血壓測量特徵 (Blood Pressure Measurement) - 屬性: Indicate
        BluetoothGattCharacteristic measurementCharacteristic = new BluetoothGattCharacteristic(
                BLOOD_PRESSURE_MEASUREMENT_UUID,
                BluetoothGattCharacteristic.PROPERTY_INDICATE,
                0 // 無讀寫權限，數據只能通過 Indication 獲取
        );
        measurementCharacteristic.addDescriptor(new BluetoothGattDescriptor(CCCD_UUID, BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));
        service.addCharacteristic(measurementCharacteristic);

        // 2. 血壓功能特徵 (Blood Pressure Feature) - 屬性: Read
        BluetoothGattCharacteristic featureCharacteristic = new BluetoothGattCharacteristic(
                BLOOD_PRESSURE_FEATURE_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );
        // 設定功能值。根據`BloodPressureFeatureCharacteristic.kt`，這裡設定一個簡單的初始值。
        // 0x0000 表示不支援任何額外功能(如身體移動偵測、袖帶適應性等)。
        featureCharacteristic.setValue(new byte[]{0x00, 0x00});
        service.addCharacteristic(featureCharacteristic);

        return service;
    }

    private BluetoothGattService createDeviceTimeService() {
        final UUID DEVICE_TIME_SERVICE_UUID = UUID.fromString("00001847-0000-1000-8000-00805f9b34fb");
        final BluetoothGattService service = new BluetoothGattService(DEVICE_TIME_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        final UUID DEVICE_TIME_FEATURE_UUID = UUID.fromString("00002B8E-0000-1000-8000-00805f9b34fb");
        final UUID DEVICE_TIME_PARAMETERS_UUID = UUID.fromString("00002B8F-0000-1000-8000-00805f9b34fb");
        final UUID DEVICE_TIME_UUID = UUID.fromString("00002B90-0000-1000-8000-00805f9b34fb");
        final UUID DEVICE_TIME_CONTROL_POINT_UUID = UUID.fromString("00002B91-0000-1000-8000-00805f9b34fb");
        final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");


        // 1. Device Time Feature Characteristic (0x2B8E) - 可讀, 指示 (Indicate)
        BluetoothGattCharacteristic featureCharacteristic = new BluetoothGattCharacteristic(
                DEVICE_TIME_FEATURE_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_INDICATE,
                BluetoothGattCharacteristic.PERMISSION_READ);
        // 根據規格，這個值是一個 bit field，表示設備支持的功能。
        // 這裡我們假設支持手動設置時間 (bit 0 = 1)，並且時間源不是原子鐘或NTP (bit 1-3 = 0)
        featureCharacteristic.setValue(new byte[]{0b0000_0001}); // 假設支持手動設置時間
        BluetoothGattDescriptor featureCccd = new BluetoothGattDescriptor(CCCD_UUID, BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        featureCccd.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        featureCharacteristic.addDescriptor(featureCccd);
        service.addCharacteristic(featureCharacteristic);


        // 2. Device Time Characteristic (0x2B90) - 可讀, 可寫
        BluetoothGattCharacteristic timeCharacteristic = new BluetoothGattCharacteristic(
                DEVICE_TIME_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
        timeCharacteristic.setValue(GattValueBuilder.forDeviceTime(Calendar.getInstance()));
        service.addCharacteristic(timeCharacteristic);


        // 3. Device Time Control Point (0x2B91) - 可寫, 指示 (Indicate)
        BluetoothGattCharacteristic controlPointCharacteristic = new BluetoothGattCharacteristic(
                DEVICE_TIME_CONTROL_POINT_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_INDICATE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);
        BluetoothGattDescriptor controlPointCccd = new BluetoothGattDescriptor(CCCD_UUID, BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        controlPointCccd.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        controlPointCharacteristic.addDescriptor(controlPointCccd);
        service.addCharacteristic(controlPointCharacteristic);


        // 4. Device Time Parameters (0x2B8F) - 可讀 (此處為可選，且簡化)
        // 這個特徵定義了時間更新的參數，結構較複雜。此處僅作佔位，設為可讀。
        BluetoothGattCharacteristic paramsCharacteristic = new BluetoothGattCharacteristic(
                DEVICE_TIME_PARAMETERS_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);
        // 0x01 = Update (Time changed, either locally or by a Set Time command)
        paramsCharacteristic.setValue(new byte[]{0x01});
        service.addCharacteristic(paramsCharacteristic);

        return service;
    }

    private BluetoothGattService createGlucoseService() {
        final UUID GLUCOSE_SERVICE_UUID = UUID.fromString("00001808-0000-1000-8000-00805f9b34fb");
        final BluetoothGattService service = new BluetoothGattService(GLUCOSE_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        final UUID GLUCOSE_MEASUREMENT_UUID = UUID.fromString("00002A18-0000-1000-8000-00805f9b34fb");
        final UUID GLUCOSE_FEATURE_UUID = UUID.fromString("00002A51-0000-1000-8000-00805f9b34fb");
        final UUID RACP_UUID = UUID.fromString("00002A52-0000-1000-8000-00805f9b34fb");
        final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

        // 1. Glucose Measurement Characteristic (0x2A18) - 通知 (Notify)
        BluetoothGattCharacteristic measurementCharacteristic = new BluetoothGattCharacteristic(
                GLUCOSE_MEASUREMENT_UUID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                0 /* no permissions */);
        BluetoothGattDescriptor measurementCccd = new BluetoothGattDescriptor(CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        measurementCccd.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        measurementCharacteristic.addDescriptor(measurementCccd);
        service.addCharacteristic(measurementCharacteristic);

        // 2. Glucose Feature Characteristic (0x2A51) - 唯讀 (Read)
        BluetoothGattCharacteristic featureCharacteristic = new BluetoothGattCharacteristic(
                GLUCOSE_FEATURE_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);
        // 設定功能值，這是一個 16-bit 的點陣圖。
        // 假設支援毛細管全血 (bit 4) 和指尖採樣 (bit 8)。
        // 0b00000001 00010000 -> 0x0110
        int featureValue = 0x0110;
        ByteBuffer buffer = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) featureValue);
        featureCharacteristic.setValue(buffer.array());
        service.addCharacteristic(featureCharacteristic);

        // 3. Record Access Control Point (RACP) (0x2A52) - 寫入/指示 (Write/Indicate)
        BluetoothGattCharacteristic racpCharacteristic = new BluetoothGattCharacteristic(
                RACP_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_INDICATE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);
        BluetoothGattDescriptor racpCccd = new BluetoothGattDescriptor(CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        racpCccd.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        racpCharacteristic.addDescriptor(racpCccd);
        service.addCharacteristic(racpCharacteristic);

        return service;
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
        BluetoothGattService service = new BluetoothGattService(DEVICE_INFO_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        // --- 1. Manufacturer Name String (0x2A29) ---
        final UUID MANUFACTURER_NAME_UUID = UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb");
        BluetoothGattCharacteristic manufacturerNameChar = new BluetoothGattCharacteristic(
                MANUFACTURER_NAME_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );
        // 設定一個範例製造商名稱
        manufacturerNameChar.setValue("Android BLE Project");

        // --- 2. Model Number String (0x2A24) ---
        final UUID MODEL_NUMBER_UUID = UUID.fromString("00002A24-0000-1000-8000-00805f9b34fb");
        BluetoothGattCharacteristic modelNumberChar = new BluetoothGattCharacteristic(
                MODEL_NUMBER_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );
        modelNumberChar.setValue("SimBLE-GATT-1000");

        // --- 3. Serial Number String (0x2A25) ---
        final UUID SERIAL_NUMBER_UUID = UUID.fromString("00002A25-0000-1000-8000-00805f9b34fb");
        BluetoothGattCharacteristic serialNumberChar = new BluetoothGattCharacteristic(
                SERIAL_NUMBER_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );
        serialNumberChar.setValue("SN-20251220-001");

        // --- 4. Hardware Revision String (0x2A27) ---
        final UUID HARDWARE_REVISION_UUID = UUID.fromString("00002A27-0000-1000-8000-00805f9b34fb");
        BluetoothGattCharacteristic hardwareRevisionChar = new BluetoothGattCharacteristic(
                HARDWARE_REVISION_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );
        hardwareRevisionChar.setValue("1.0.1");

        // --- 5. Firmware Revision String (0x2A26) ---
        final UUID FIRMWARE_REVISION_UUID = UUID.fromString("00002A26-0000-1000-8000-00805f9b34fb");
        BluetoothGattCharacteristic firmwareRevisionChar = new BluetoothGattCharacteristic(
                FIRMWARE_REVISION_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );
        firmwareRevisionChar.setValue("1.2.3");

        // --- 6. Software Revision String (0x2A28) ---
        final UUID SOFTWARE_REVISION_UUID = UUID.fromString("00002A28-0000-1000-8000-00805f9b34fb");
        BluetoothGattCharacteristic softwareRevisionChar = new BluetoothGattCharacteristic(
                SOFTWARE_REVISION_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );
        softwareRevisionChar.setValue("2.0.0-rc1");

        // --- 7. System ID (0x2A23) ---
        final UUID SYSTEM_ID_UUID = UUID.fromString("00002A23-0000-1000-8000-00805f9b34fb");
        BluetoothGattCharacteristic systemIdChar = new BluetoothGattCharacteristic(
                SYSTEM_ID_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );
        // 格式: 64-bit Manufacturer Identifier + 24-bit Organizationally Unique Identifier
        // 這裡我們使用一個範例值
        ByteBuffer systemIdBuffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        systemIdBuffer.putLong(0x1122334455667788L);
        systemIdChar.setValue(systemIdBuffer.array());

        // --- 8. IEEE 11073-20601 Regulatory Certification Data List (0x2A2A) ---
        final UUID REGULATORY_CERT_UUID = UUID.fromString("00002A2A-0000-1000-8000-00805f9b34fb");
        BluetoothGattCharacteristic regulatoryCertChar = new BluetoothGattCharacteristic(
                REGULATORY_CERT_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );
        // 這裡我們建立一個範例的 FCC 認證數據
        // 格式為 TLV (Type-Length-Value)
        byte[] fccIdBytes = "FCC ID: ABC-12345".getBytes(StandardCharsets.UTF_8);
        // 總長度 = 認證機構(2) + 類型(1) + 實際數據長度
        int valueLength = 2 + 1 + fccIdBytes.length;
        ByteBuffer regulatoryBuffer = ByteBuffer.allocate(2 + valueLength).order(ByteOrder.LITTLE_ENDIAN);
        regulatoryBuffer.put((byte) 0x01); // Type: 0x01 (認證機構列表)
        regulatoryBuffer.put((byte) valueLength); // Length: 後續所有數據的長度
        regulatoryBuffer.putShort((short) 0x0100); // Value - 認證機構: 0x0100 (範例代碼)
        regulatoryBuffer.put((byte) 0x01); // Value - 認證類型: 0x01 (範例類型, 如 FCC Part 15)
        regulatoryBuffer.put(fccIdBytes); // Value - 實際認證字串
        regulatoryCertChar.setValue(regulatoryBuffer.array());

        // --- 9. PnP ID (0x2A50) ---
        final UUID PNP_ID_UUID = UUID.fromString("00002A50-0000-1000-8000-00805f9b34fb");
        BluetoothGattCharacteristic pnpIdChar = new BluetoothGattCharacteristic(
                PNP_ID_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );
        // 格式: 1 byte Vendor ID Source, 2 bytes Vendor ID, 2 bytes Product ID, 2 bytes Product Version
        ByteBuffer pnpIdBuffer = ByteBuffer.allocate(7).order(ByteOrder.LITTLE_ENDIAN);
        pnpIdBuffer.put((byte) 0x01);      // Vendor ID Source: 0x01 = Bluetooth SIG assigned
        pnpIdBuffer.putShort((short) 0x00E0); // Vendor ID: 0x00E0 = Google, Inc. (範例)
        pnpIdBuffer.putShort((short) 0xABCD); // Product ID: 0xABCD (自訂產品ID)
        pnpIdBuffer.putShort((short) 0x0101); // Product Version: 0x0101 (代表 1.0.1)
        pnpIdChar.setValue(pnpIdBuffer.array());

        // 將所有特徵加入服務中
        service.addCharacteristic(manufacturerNameChar);
        service.addCharacteristic(modelNumberChar);
        service.addCharacteristic(serialNumberChar);
        service.addCharacteristic(hardwareRevisionChar);
        service.addCharacteristic(firmwareRevisionChar);
        service.addCharacteristic(softwareRevisionChar);
        service.addCharacteristic(systemIdChar);
        service.addCharacteristic(regulatoryCertChar);
        service.addCharacteristic(pnpIdChar);

        return service;
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
        if (dayOfWeek == Calendar.SUNDAY) {
            dayOfWeek = 7;
        } else {
            dayOfWeek -= 1;
        } // SIG: Monday=1..Sunday=7

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

//    private BluetoothGattService createWeightScaleService() {
//        final UUID WEIGHT_SCALE_SERVICE_UUID = UUID.fromString("0000181D-0000-1000-8000-00805f9b34fb");
//        final BluetoothGattService service = new BluetoothGattService(WEIGHT_SCALE_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
//
//        final UUID WEIGHT_SCALE_MEASUREMENT_UUID = UUID.fromString("00002A9D-0000-1000-8000-00805f9b34fb");
//        final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
//
//        // 1. Weight Scale Measurement Characteristic (0x2A9D) - 指示 (Indicate)
//        BluetoothGattCharacteristic measurementCharacteristic = new BluetoothGattCharacteristic(
//                WEIGHT_SCALE_MEASUREMENT_UUID,
//                BluetoothGattCharacteristic.PROPERTY_INDICATE,
//                BluetoothGattCharacteristic.PERMISSION_READ);
//
//        // 設定初始值 (例如 65.5 kg)
//        measurementCharacteristic.setValue(GattValueBuilder.forWeightScaleMeasurement(65.5f));
//
//        // 加入 CCCD (Client Characteristic Configuration Descriptor) 以啟用 Indication
//        BluetoothGattDescriptor cccd = new BluetoothGattDescriptor(CCCD_UUID,
//                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
//        cccd.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
//        measurementCharacteristic.addDescriptor(cccd);
//
//        service.addCharacteristic(measurementCharacteristic);
//
//        // 根據SIG標準，Weight Scale Service還有一個可選的 Weight Scale Feature (0x2A9E) 特徵。
//        // 這裡為了簡化，我們僅實作必要的 Measurement 特徵。
//
//        return service;
//    }

    private BluetoothGattService createHearingAidService() {
        final UUID HEARING_AID_SERVICE_UUID = UUID.fromString("00001854-0000-1000-8000-00805f9b34fb");
        final BluetoothGattService service = new BluetoothGattService(HEARING_AID_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        final UUID FEATURES_UUID = UUID.fromString("00002FD9-0000-1000-8000-00805f9b34fb");
        final UUID PRESET_CONTROL_POINT_UUID = UUID.fromString("00002FDB-0000-1000-8000-00805f9b34fb");
        final UUID ACTIVE_PRESET_INDEX_UUID = UUID.fromString("00002FDC-0000-1000-8000-00805f9b34fb");
        final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

        // 1. Hearing Aid Features (0x2FD9) - 唯讀 (Read)
        BluetoothGattCharacteristic featuresCharacteristic = new BluetoothGattCharacteristic(
                FEATURES_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );
        // 設定功能值:
        // 假設為雙耳裝置(binaural, bit 0=1), 支援可寫的預設(bit 1=1), 支援4個預設(bit 2=0, bit 3=1)
        // 0b00001111 -> 0x0F
        byte[] featureValue = new byte[]{0x0F};
        featuresCharacteristic.setValue(featureValue);
        service.addCharacteristic(featuresCharacteristic);

        // 2. Preset Control Point (0x2FDB) - 寫入/通知 (Write/Notify)
        // 這個特徵由客戶端寫入來控制預設。我們需要在 onCharacteristicWriteRequest 中處理邏輯。
        BluetoothGattCharacteristic presetCpCharacteristic = new BluetoothGattCharacteristic(
                PRESET_CONTROL_POINT_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_WRITE
        );
        BluetoothGattDescriptor presetCpCccd = new BluetoothGattDescriptor(CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        presetCpCccd.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        presetCpCharacteristic.addDescriptor(presetCpCccd);
        service.addCharacteristic(presetCpCharacteristic);

        // 3. Active Preset Index (0x2FDC) - 讀取/通知 (Read/Notify)
        BluetoothGattCharacteristic activePresetCharacteristic = new BluetoothGattCharacteristic(
                ACTIVE_PRESET_INDEX_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
        );
        // 初始預設為 1
        activePresetCharacteristic.setValue(GattValueBuilder.forActivePresetIndex(1));
        BluetoothGattDescriptor activePresetCccd = new BluetoothGattDescriptor(CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        activePresetCccd.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        activePresetCharacteristic.addDescriptor(activePresetCccd);
        service.addCharacteristic(activePresetCharacteristic);

        return service;
    }

    private BluetoothGattService createPulseOximeterService() {
        final UUID PLX_SERVICE_UUID = UUID.fromString("00001822-0000-1000-8000-00805f9b34fb");
        final BluetoothGattService service = new BluetoothGattService(PLX_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        final UUID PLX_SPOT_CHECK_UUID = UUID.fromString("00002A5E-0000-1000-8000-00805f9b34fb");
        final UUID PLX_FEATURES_UUID = UUID.fromString("00002A60-0000-1000-8000-00805f9b34fb");
        final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

        // 1. PLX Spot-Check Measurement Characteristic (0x2A5E) - 通知 (Notify)
        BluetoothGattCharacteristic measurementCharacteristic = new BluetoothGattCharacteristic(
                PLX_SPOT_CHECK_UUID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                0 /* no permissions */);
        BluetoothGattDescriptor measurementCccd = new BluetoothGattDescriptor(CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        measurementCccd.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        measurementCharacteristic.addDescriptor(measurementCccd);
        service.addCharacteristic(measurementCharacteristic);

        // 2. PLX Features Characteristic (0x2A60) - 唯讀 (Read)
        BluetoothGattCharacteristic featuresCharacteristic = new BluetoothGattCharacteristic(
                PLX_FEATURES_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);

        // 設定功能值，這是一個 16-bit 的點陣圖。
        // 假設支援時間戳 (bit 3) 和脈率 (bit 4)。
        // 0b00011000 -> 0x18
        int featureValue = 0b00011000;
        ByteBuffer buffer = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) featureValue);
        featuresCharacteristic.setValue(buffer.array());
        service.addCharacteristic(featuresCharacteristic);

        return service;
    }

    private BluetoothGattService createWeightScaleService() {
        final UUID WEIGHT_SCALE_SERVICE_UUID = UUID.fromString("0000181D-0000-1000-8000-00805f9b34fb");
        final BluetoothGattService service = new BluetoothGattService(WEIGHT_SCALE_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        final UUID WEIGHT_SCALE_MEASUREMENT_UUID = UUID.fromString("00002A9D-0000-1000-8000-00805f9b34fb");
        final UUID WEIGHT_SCALE_FEATURE_UUID = UUID.fromString("00002A9E-0000-1000-8000-00805f9b34fb"); // Feature UUID
        final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");


        // 1. Weight Scale Measurement Characteristic (0x2A9D) - 指示 (Indicate)
        BluetoothGattCharacteristic measurementCharacteristic = new BluetoothGattCharacteristic(
                WEIGHT_SCALE_MEASUREMENT_UUID,
                BluetoothGattCharacteristic.PROPERTY_INDICATE,
                BluetoothGattCharacteristic.PERMISSION_READ);

        // 設定初始值 (例如 65.5 kg)
        measurementCharacteristic.setValue(GattValueBuilder.forWeightScaleMeasurement(65.5f));

        // 加入 CCCD (Client Characteristic Configuration Descriptor) 以啟用 Indication
        BluetoothGattDescriptor cccd = new BluetoothGattDescriptor(CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        cccd.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        measurementCharacteristic.addDescriptor(cccd);
        service.addCharacteristic(measurementCharacteristic);

        // 2. Weight Scale Feature Characteristic (0x2A9E) - 唯讀 (Read)
        BluetoothGattCharacteristic featureCharacteristic = new BluetoothGattCharacteristic(
                WEIGHT_SCALE_FEATURE_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );

        // 設定功能值。這是一個 4-byte 的點陣圖。
        // 根據 SIG 標準，每個位元代表一項功能。
        // 例如:
        // bit 0: 時間戳支援 (1 = yes)
        // bit 1: 多使用者支援 (1 = yes)
        // bit 2: BMI 支援 (1 = yes)
        // bit 3-4: 重量解析度
        // bit 5-7: 高度解析度
        // ...
        // 為了簡化，我們假設此設備不支援時間戳、多使用者和 BMI，且重量解析度為 0.005kg。
        // 對應到規格:
        // - Weight Resolution: 0b0100 (±0.005kg) -> 位元 3,4
        // - Height Resolution: 0b000 (Not specified)
        // 值 = 0b00000000 00000000 00010000 00000000 -> 0x00010000 (錯誤，應該是低位元)
        // 值 = 0b00000000 00000000 00000000 00010000 -> 0x00000010
        // Weight Resolution bits 3,4。 0b0100 -> 代表 4。
        // 將 bit 4 設為 1。
        int featureValue = 0b00010000; // 0x10

        // 使用 ByteBuffer 來確保位元組順序 (LITTLE_ENDIAN)
        ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(featureValue);
        featureCharacteristic.setValue(buffer.array());
        service.addCharacteristic(featureCharacteristic);

        return service;
    }

    private BluetoothGattService createEnvironmentalSensingService() {
        final UUID ESS_UUID = UUID.fromString("0000181A-0000-1000-8000-00805f9b34fb");
        final BluetoothGattService service = new BluetoothGattService(ESS_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        final UUID TEMPERATURE_UUID = UUID.fromString("00002A6E-0000-1000-8000-00805f9b34fb");
        final UUID HUMIDITY_UUID = UUID.fromString("00002A6F-0000-1000-8000-00805f9b34fb");
        final UUID PRESSURE_UUID = UUID.fromString("00002A6D-0000-1000-8000-00805f9b34fb");
        final UUID WIND_CHILL_UUID = UUID.fromString("00002A79-0000-1000-8000-00805f9b34fb");
        final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

        // 將 Read 和 Notify 屬性應用到所有特徵
        final int properties = BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY;
        final int permissions = BluetoothGattCharacteristic.PERMISSION_READ;

        // 1. Temperature Characteristic (0x2A6E)
        BluetoothGattCharacteristic tempChar = new BluetoothGattCharacteristic(TEMPERATURE_UUID, properties, permissions);
        tempChar.addDescriptor(new BluetoothGattDescriptor(CCCD_UUID, BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));
        service.addCharacteristic(tempChar);

        // 2. Humidity Characteristic (0x2A6F)
        BluetoothGattCharacteristic humidityChar = new BluetoothGattCharacteristic(HUMIDITY_UUID, properties, permissions);
        humidityChar.addDescriptor(new BluetoothGattDescriptor(CCCD_UUID, BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));
        service.addCharacteristic(humidityChar);

        // 3. Pressure Characteristic (0x2A6D)
        BluetoothGattCharacteristic pressureChar = new BluetoothGattCharacteristic(PRESSURE_UUID, properties, permissions);
        pressureChar.addDescriptor(new BluetoothGattDescriptor(CCCD_UUID, BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));
        service.addCharacteristic(pressureChar);

        // 4. Wind Chill Characteristic (0x2A79)
        BluetoothGattCharacteristic windChillChar = new BluetoothGattCharacteristic(WIND_CHILL_UUID, properties, permissions);
        windChillChar.addDescriptor(new BluetoothGattDescriptor(CCCD_UUID, BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));
        service.addCharacteristic(windChillChar);

        return service;
    }

    private BluetoothGattService createCyclingPowerService() {
        final UUID CPS_SERVICE_UUID = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb");
        final BluetoothGattService service = new BluetoothGattService(CPS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        final UUID CP_MEASUREMENT_UUID = UUID.fromString("00002A63-0000-1000-8000-00805f9b34fb");
        final UUID CP_FEATURE_UUID = UUID.fromString("00002A65-0000-1000-8000-00805f9b34fb");
        final UUID SENSOR_LOCATION_UUID = UUID.fromString("00002A5D-0000-1000-8000-00805f9b34fb");
        final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

        // 1. Cycling Power Measurement Characteristic (0x2A63) - 通知 (Notify)
        BluetoothGattCharacteristic measurementCharacteristic = new BluetoothGattCharacteristic(
                CP_MEASUREMENT_UUID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                0 /* no permissions */);
        BluetoothGattDescriptor measurementCccd = new BluetoothGattDescriptor(CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        measurementCccd.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        measurementCharacteristic.addDescriptor(measurementCccd);
        service.addCharacteristic(measurementCharacteristic);

        // 2. Cycling Power Feature Characteristic (0x2A65) - 唯讀 (Read)
        BluetoothGattCharacteristic featuresCharacteristic = new BluetoothGattCharacteristic(
                CP_FEATURE_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);

        // 設定功能值，這是一個 32-bit 的點陣圖。
        // 假設支援踏頻 (bit 1)
        // 0b00000000_00000000_00000000_00000100 -> 0x04
        int featureValue = 0x04;
        ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(featureValue);
        featuresCharacteristic.setValue(buffer.array());
        service.addCharacteristic(featuresCharacteristic);

        // 3. Sensor Location Characteristic (0x2A5D) - 唯讀 (Read)
        BluetoothGattCharacteristic locationCharacteristic = new BluetoothGattCharacteristic(
                SENSOR_LOCATION_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);
        // 假設感測器位置在左曲柄 (Left Crank = 4)
        locationCharacteristic.setValue(new byte[]{ (byte) 4 });
        service.addCharacteristic(locationCharacteristic);

        return service;
    }

    private BluetoothGattService createFitnessMachineService() {
        final UUID FTMS_UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb");
        final BluetoothGattService service = new BluetoothGattService(FTMS_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

        // 1. **Fitness Machine Feature (0x2ACC) - 唯讀** (非常重要)
        //    宣告此設備支援的功能。客戶端會首先讀取此特徵。
        final UUID FTMS_FEATURE_UUID = UUID.fromString("00002ACC-0000-1000-8000-00805f9b34fb");
        BluetoothGattCharacteristic featureChar = new BluetoothGattCharacteristic(
                FTMS_FEATURE_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );
        // Flags: 宣告支援踏頻、心率、功率、阻力控制和距離。
        // 這是一個 32-bit 的 bit-mask。
        int featureFlags = 0b0000_0000_0000_0110_0100_0000_0000_0110;
        // bit 1: Cadence
        // bit 2: Total Distance
        // bit 8: Heart Rate
        // bit 9: Resistance Control
        // bit 13: Power Measurement
        ByteBuffer featureBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        featureBuffer.putInt(featureFlags);
        featureChar.setValue(featureBuffer.array());
        service.addCharacteristic(featureChar);


        // 2. **Indoor Bike Data (0x2AD2) - 可通知**
        //    (我們之前已經建立了它的引用 indoorBikeDataCharacteristic)
        final UUID INDOOR_BIKE_DATA_UUID = UUID.fromString("00002AD2-0000-1000-8000-00805f9b34fb");
        BluetoothGattCharacteristic indoorBikeDataChar = new BluetoothGattCharacteristic(
                INDOOR_BIKE_DATA_UUID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                0
        );
        indoorBikeDataChar.addDescriptor(new BluetoothGattDescriptor(CCCD_UUID, BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));
        service.addCharacteristic(indoorBikeDataChar);

        // 你也可以在這裡加入 Treadmill (0x2ACD) 和 Cross Trainer (0x2ACE) 的特徵，如果需要同時模擬的話。


        // 3. **Fitness Machine Control Point (0x2AD9) - 可寫/可指示** (非常重要)
        //    接收來自客戶端的命令。
        final UUID FTMS_CONTROL_POINT_UUID = UUID.fromString("00002AD9-0000-1000-8000-00805f9b34fb");
        BluetoothGattCharacteristic controlPointChar = new BluetoothGattCharacteristic(
                FTMS_CONTROL_POINT_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_INDICATE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
        );
        controlPointChar.addDescriptor(new BluetoothGattDescriptor(CCCD_UUID, BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));
        service.addCharacteristic(controlPointChar);

        // 4. **Fitness Machine Status (0x2ADA) - 可通知**
        //    回報設備狀態。
        final UUID FTMS_STATUS_UUID = UUID.fromString("00002ADA-0000-1000-8000-00805f9b34fb");
        BluetoothGattCharacteristic statusChar = new BluetoothGattCharacteristic(
                FTMS_STATUS_UUID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                0
        );
        statusChar.addDescriptor(new BluetoothGattDescriptor(CCCD_UUID, BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));
        service.addCharacteristic(statusChar);


        return service;
    }
}
