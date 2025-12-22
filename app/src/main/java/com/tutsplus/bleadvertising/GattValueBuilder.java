package com.tutsplus.bleadvertising;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * 一個輔助類別，使用建造者模式（Builder-style methods）來封裝建立標準 GATT 特徵值的複雜邏輯。
 * 將高階數據類型 (int, float, etc.) 轉換為符合 SIG 標準的 byte[]。
 */
public final class GattValueBuilder {

    // 私有建構函式，防止實例化
    private GattValueBuilder() {}

    /**
     * 封裝電池電量 (0x2A19) 的值。
     * @param batteryLevel 電量百分比 (0-100)。
     * @return 符合格式的 byte[]。
     */
    public static byte[] forBatteryLevel(int batteryLevel) {
        // 使用 ByteBuffer 來確保正確的格式和順序，雖然這裡只有一個 byte
        return ByteBuffer.allocate(1)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put((byte) batteryLevel)
                .array();
    }

    /**
     * 封裝血壓測量 (0x2A35) 的值。
     * 血壓單位為 mmHg，脈率單位為 bpm。
     *
     * @param systolic 收縮壓 (SFLOAT)。
     * @param diastolic 舒張壓 (SFLOAT)。
     * @param meanArterialPressure 平均動脈壓 (SFLOAT)。
     * @param pulseRate 脈率 (SFLOAT)。
     * @param hasPulseRate 如果包含脈率數據，設為 true。
     * @return 符合格式的 byte[]。
     */
    public static byte[] forBloodPressureMeasurement(float systolic, float diastolic, float meanArterialPressure, float pulseRate, boolean hasPulseRate) {
        // Flags: 單位為 mmHg, 不含時間戳, 含脈率
        byte flags = (byte) (hasPulseRate ? 0b0000_0010 : 0b0000_0000);
        int bufferSize = 1 + 6; // Flags + 3x SFLOAT

        if (hasPulseRate) {
            bufferSize += 2; // Pulse Rate (SFLOAT)
        }

        ByteBuffer buffer = ByteBuffer.allocate(bufferSize).order(ByteOrder.LITTLE_ENDIAN);

        // 1. Flags
        buffer.put(flags);

        // 2. 血壓測量值 (收縮壓, 舒張壓, 平均動脈壓) - SFLOAT
        // SFLOAT 是一種 16-bit 浮點數，由 4-bit 指數和 12-bit 尾數組成
        // 這裡為了簡化，我們直接使用 IEEE-754 32-bit float，但客戶端需要知道如何解析
        // 為了更符合規範，應將 float 轉為 SFLOAT，但這需要額外的轉換邏輯
        // 此處我們將其直接當作 Float 處理，並佔用相應字節空間
        // SFLOAT 的字節長度是 2 bytes (16-bit)
        // SFLOAT 是一種16位元的浮點數。這裡為了簡化，我們直接將 float 轉為 short。
        // 在真實設備上，需要嚴格按照 SFLOAT (12位尾數 + 4位指數) 格式轉換。

        buffer.putShort((short) systolic);
        buffer.putShort((short) diastolic);
        buffer.putShort((short) meanArterialPressure);

        // 3. 脈率 (可選)
        if (hasPulseRate) {
            buffer.putShort((short) pulseRate);
        }

        return buffer.array();
    }


    /**
     * 封裝 Device Time (0x2B90) 的值。
     * 結構與 Current Time 相似，但不包含 Adjust Reason。
     * @param calendar 一個 Calendar 物件，代表要設定的時間。
     * @return 符合格式的 byte[]。
     */
    public static byte[] forDeviceTime(Calendar calendar) {
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1; // Month is 0-based
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK); // Sunday=1, ..., Saturday=7
        // SIG 標準: Monday=1..Sunday=7
        if (dayOfWeek == Calendar.SUNDAY) {
            dayOfWeek = 7;
        } else {
            dayOfWeek -= 1;
        }

        // 使用 ByteBuffer 確保位元組順序正確 (LITTLE_ENDIAN)
        // 總共 9 bytes: Year(2) + Month(1) + Day(1) + Hour(1) + Minute(1) + Second(1) + DayOfWeek(1) + Fractions256(1)
        ByteBuffer buffer = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) year);
        buffer.put((byte) month);
        buffer.put((byte) day);
        buffer.put((byte) hour);
        buffer.put((byte) minute);
        buffer.put((byte) second);
        buffer.put((byte) dayOfWeek);
        buffer.put((byte) 0); // Fractions256

        return buffer.array();
    }

    /**
     * 封裝血糖測量 (0x2A18) 的值。
     * @param sequenceNumber 序列號，每個測量值遞增。
     * @param glucoseConcentration 血糖濃度，單位 mg/dL。
     * @param includeTimeOffset 是否包含時間偏移。
     * @param includeTypeAndLocation 是否包含測量類型和位置。
     * @return 符合格式的 byte[]。
     */
    public static byte[] forGlucoseMeasurement(int sequenceNumber, float glucoseConcentration, boolean includeTimeOffset, boolean includeTypeAndLocation) {
        // --- Flags (1 byte) ---
        // bit 0: Time Offset Present (1 = true)
        // bit 1: Glucose Concentration, Type, and Sample Location Present (1 = true)
        // bit 2: Glucose Concentration Units (0 = kg/L, 1 = mol/L) - 我們使用 mg/dL，所以這個位元在此無效，但會被包含。
        // bit 3: Sensor Status Annunciation Present (1 = true)
        byte flags = 0;
        if (includeTimeOffset) {
            flags |= 0b0000_0001;
        }
        if (includeTypeAndLocation) {
            flags |= 0b0000_0010;
        }
        // 為了簡化，我們不包含 Sensor Status (bit 3)
        // 濃度單位將使用 SFLOAT 表示 mg/dL，而不是 mol/L。

        // --- 計算緩衝區大小 ---
        // Flags(1) + SeqNum(2) + BaseTime(7) + Conc(2) = 12 bytes (基本)
        int bufferSize = 1 + 2 + 7; // Flags, Sequence Number, Base Time
        if (includeTimeOffset) {
            bufferSize += 2; // Time Offset (SINT16)
        }
        if (includeTypeAndLocation) {
            bufferSize += 3; // Concentration (SFLOAT) + Type/Location(1)
        } else {
            bufferSize += 2; // Concentration (SFLOAT)
        }

        ByteBuffer buffer = ByteBuffer.allocate(bufferSize).order(ByteOrder.LITTLE_ENDIAN);

        // 1. Flags
        buffer.put(flags);

        // 2. Sequence Number (UINT16)
        buffer.putShort((short) sequenceNumber);

        // 3. Base Time (DateTime) - 7 bytes
        Calendar now = Calendar.getInstance();
        buffer.putShort((short) now.get(Calendar.YEAR));
        buffer.put((byte) (now.get(Calendar.MONTH) + 1));
        buffer.put((byte) now.get(Calendar.DAY_OF_MONTH));
        buffer.put((byte) now.get(Calendar.HOUR_OF_DAY));
        buffer.put((byte) now.get(Calendar.MINUTE));
        buffer.put((byte) now.get(Calendar.SECOND));

        // 4. Time Offset (SINT16) - 可選
        if (includeTimeOffset) {
            buffer.putShort((short) 0); // 假設沒有時間偏移
        }

        // 5. Glucose Concentration (SFLOAT, mg/dL)
        // SFLOAT 是一種16位元的浮點數。這裡為了簡化，我們直接將 float 轉為 short。
        buffer.putShort((short) glucoseConcentration);

        // 6. Type and Sample Location (Nibble-Nibble) - 可選
        if (includeTypeAndLocation) {
            // Type: 1 (毛細管全血), Location: 1 (指尖) -> 0x11
            byte typeAndLocation = 0x11;
            buffer.put(typeAndLocation);
        }

        return buffer.array();
    }

    /**
     * 封裝助聽器當前預設索引 (Active Preset Index, 0x2FDC) 的值。
     * •Hearing Aid Features (助聽器功能): 0x2FD9 - 唯讀 (Read)，描述助聽器支援的功能，例如它是單耳還是雙耳、是否支援音量控制等。
     * •Preset Control Point (預設控制點): 0x2FDB - 可寫/可通知 (Write/Notify)，用於控制和切換助聽器的預設模式 (如 "正常"、"餐廳"、"戶外" 模式)。
     * •Active Preset Index (當前預設索引): 0x2FDC - 唯讀/可通知 (Read/Notify)，回報當前正在使用的預設模式是哪一個。
     * @param presetIndex 當前的預設模式索引。
     * @return 符合格式的 byte[]。
     */
    public static byte[] forActivePresetIndex(int presetIndex) {
        // Active Preset Index 是一個 UINT8 值。
        return new byte[]{(byte) presetIndex};
    }


    /**
     * Fitness Machine Feature (健身器材功能): 0x2ACC - 唯讀 (Read)，
     * 這非常重要，它告訴客戶端 (如 Zwift App) 這台設備支援哪些功能（例如，速度、踏頻、功率、心率、阻力控制等）。
     * •Treadmill Data (跑步機數據) /
     * Cross Trainer Data (橢圓機數據) /
     * Indoor Bike Data (室內自行車數據) 等: 0x2ACD / 0x2ACE / 0x2AD2 - 可通知 (Notify)，用於發送即時運動數據。
     * 我們將重點實作 Indoor Bike Data (0x2AD2)，因為這是智慧騎行台最核心的特徵。•Fitness Machine Control Point (健身器材控制點): 0x2AD9 - 可寫/可指示 (Write/Indicate)，這是客戶端向健身設備發送命令的入口，例如請求開始、停止、重置，以及最重要的設定目標阻力/功率。
     * •Fitness Machine Status (健身器材狀態): 0x2ADA - 可通知 (Notify)，用於回報設備的當前狀態（例如，已準備好、正在運行等）
     * 封裝室內自行車數據 (Indoor Bike Data, 0x2AD2) 的值。
     * @param speed 瞬時速度 (UINT16, 單位 km/h, 解析度 0.01)。
     * @param cadence 瞬時踏頻 (UINT16, 單位 rpm, 解析度 0.5)。
     * @param power 瞬時功率 (SINT16, 單位 watts)。
     * @param heartRate 心率 (bpm)
     * @param totalDistance 總距離 (meters)
     * @return 符合格式的 byte[]。
     */
    public static byte[] forIndoorBikeData(float speed, float cadence, int power, int heartRate, int totalDistance) {
        // --- Flags (2 bytes, LITTLE_ENDIAN) ---
        // bit 1: Instantaneous Speed
        // bit 2: Instantaneous Cadence
        // bit 4: Instantaneous Power
        // bit 5: Heart Rate
        // bit 8: Total Distance
        short flags = 0b0000_0001_0011_0110; // 0x0136

        // --- 計算緩衝區大小 ---
        // Flags(2) + Speed(2) + Cadence(2) + Power(2) + HeartRate(1) + TotalDistance(3)
        int bufferSize = 2 + 2 + 2 + 2 + 1 + 3;

        ByteBuffer buffer = ByteBuffer.allocate(bufferSize).order(ByteOrder.LITTLE_ENDIAN);

        // 1. Flags
        buffer.putShort(flags);

        // 2. Instantaneous Speed (UINT16, km/h, resolution 0.01)
        buffer.putShort((short) (speed * 100));

        // 3. Instantaneous Cadence (UINT16, rpm, resolution 0.5)
        buffer.putShort((short) (cadence * 2));

        // 4. Instantaneous Power (SINT16, watts)
        buffer.putShort((short) power);

        // 5. Heart Rate (UINT8, bpm)
        buffer.put((byte) heartRate);

        // 6. Total Distance (UINT24, meters)
        // UINT24 需要用 3 個 bytes 來表示
        buffer.put((byte) (totalDistance & 0xFF));
        buffer.put((byte) ((totalDistance >> 8) & 0xFF));
        buffer.put((byte) ((totalDistance >> 16) & 0xFF));

        return buffer.array();
    }

    /**
     * 封裝跑步機數據 (Treadmill Data, 0x2ACD) 的值。
     * @param speed 瞬時速度 (km/h)
     * @param incline 坡度 (%)
     * @param totalDistance 總距離 (meters)
     * @return 符合格式的 byte[]
     */
    public static byte[] forTreadmillData(float speed, float incline, int totalDistance) {
        // Flags: bit 1 (Speed), bit 8 (Total Distance), bit 9 (Incline)
        short flags = 0b0000_0011_0000_0010; // 0x0302

        // Buffer size: Flags(2) + Speed(2) + Incline(2) + TotalDistance(3)
        int bufferSize = 2 + 2 + 2 + 3;
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize).order(ByteOrder.LITTLE_ENDIAN);

        buffer.putShort(flags);
        buffer.putShort((short) (speed * 100)); // Speed
        buffer.putShort((short) (incline * 10)); // Incline, resolution 0.1
        // Total Distance (UINT24)
        buffer.put((byte) (totalDistance & 0xFF));
        buffer.put((byte) ((totalDistance >> 8) & 0xFF));
        buffer.put((byte) ((totalDistance >> 16) & 0xFF));

        return buffer.array();
    }

    /**
     * 封裝橢圓機數據 (Cross Trainer Data, 0x2ACE) 的值。
     * @param speed 瞬時速度 (km/h)
     * @param power 瞬時功率 (watts)
     * @param totalDistance 總距離 (meters)
     * @return 符合格式的 byte[]
     */
    public static byte[] forCrossTrainerData(float speed, int power, int totalDistance) {
        // Flags: bit 1 (Speed), bit 4 (Power), bit 8 (Total Distance)
        short flags = 0b0000_0001_0001_0010; // 0x0112

        // Buffer size: Flags(2) + Speed(2) + Power(2) + TotalDistance(3)
        int bufferSize = 2 + 2 + 2 + 3;
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize).order(ByteOrder.LITTLE_ENDIAN);

        buffer.putShort(flags);
        buffer.putShort((short) (speed * 100)); // Speed
        buffer.putShort((short) power); // Power
        // Total Distance (UINT24)
        buffer.put((byte) (totalDistance & 0xFF));
        buffer.put((byte) ((totalDistance >> 8) & 0xFF));
        buffer.put((byte) ((totalDistance >> 16) & 0xFF));

        return buffer.array();
    }


    /**
     * 封裝心率測量 (0x2A37) 的值。
     * @param heartRate 心率值 (bpm)。
     * @param isUint16HeartRate 如果心率值需要用 16-bit 整數表示，設為 true。
     * @param isEnergyExpendedPresent 如果包含能量消耗數據，設為 true。
     * @param energyExpended 能量消耗值（千焦耳）。
     * @return 符合格式的 byte[]。
     */
    public static byte[] forHeartRateMeasurement(int heartRate, boolean isUint16HeartRate, boolean isEnergyExpendedPresent, int energyExpended) {
        byte flags = 0b0000_1000; // 預設：Sensor Contact Not Supported, RR-Interval present
        int bufferSize = 2; // Flags + 8-bit Heart Rate

        if (isUint16HeartRate) {
            flags |= 0b0000_0001; // 設定心率值格式為 UINT16
            bufferSize += 1;
        }
        if (isEnergyExpendedPresent) {
            flags |= 0b0000_1000; // 設定能量消耗存在
            bufferSize += 2;
        }

        ByteBuffer buffer = ByteBuffer.allocate(bufferSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(flags);

        if (isUint16HeartRate) {
            buffer.putShort((short) heartRate);
        } else {
            buffer.put((byte) heartRate);
        }

        if (isEnergyExpendedPresent) {
            buffer.putShort((short) energyExpended);
        }

        return buffer.array();
    }

    // 多載一個簡化版，只傳心率
    public static byte[] forHeartRateMeasurement(int heartRate) {
        // 預設：8-bit 心率, 不包含能量消耗
        return forHeartRateMeasurement(heartRate, false, false, 0);
    }

    /**
     * 封裝溫度測量 (0x2A1C) 的值。
     * @param temperature 溫度值（攝氏度）。
     * @return 符合格式的 byte[]。
     */
    public static byte[] forTemperatureMeasurement(float temperature) {
        byte flags = 0x00; // 預設：攝氏度, 無時間戳, 無溫度類型

        // 使用 ByteBuffer 來處理 float 和 byte 的轉換，更安全可靠
        ByteBuffer buffer = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(flags);
        buffer.putFloat(temperature);
        return buffer.array();
    }

    /**
     * 封裝當前時間 (0x2A2B) 的值。
     * @param calendar 一個 Calendar 物件，代表要設定的時間。
     * @return 符合格式的 byte[]。
     */
    public static byte[] forCurrentTime(Calendar calendar) {
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1; // Month is 0-based
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK); // Sunday=1, ..., Saturday=7
        // SIG 標準: Monday=1..Sunday=7
        if (dayOfWeek == Calendar.SUNDAY) {
            dayOfWeek = 7;
        } else {
            dayOfWeek -= 1;
        }

        // 使用 ByteBuffer 確保位元組順序正確 (LITTLE_ENDIAN)
        ByteBuffer buffer = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) year);
        buffer.put((byte) month);
        buffer.put((byte) day);
        buffer.put((byte) hour);
        buffer.put((byte) minute);
        buffer.put((byte) second);
        buffer.put((byte) dayOfWeek);
        buffer.put((byte) 0); // Fractions256
        buffer.put((byte) 1); // Adjust Reason (Manual update)

        return buffer.array();
    }

    /**
     * 封裝 ESS 的溫度 (Temperature, 0x2A6E) 的值。
     *  ESS 是一個容器服務，可以包含多種環境測量特徵。常見的有： 市面上常見的環境感測設備（如 BME280 感測器）
     *  •Temperature (0x2A6E): 溫度，這是 ESS 的標準溫度特徵，不同於健康溫度計服務 (0x1809) 中的溫度 (0x2A1C)。
     *  •Humidity (0x2A6F): 濕度。
     *  •Pressure (0x2A6D): 氣壓。
     *  •Wind Chill (0x2A79): 風寒，您提供的檔案中有此定義。
     *  •UV Index (0x2A76): 紫外線指數。
     *  •屬性: 這些特徵通常是唯讀 (Read) 和/或可通知 (Notify) 的。
     * @param temperature 溫度，單位為攝氏度。
     * @return 符合格式的 byte[]。
     */
    public static byte[] forTemperature(float temperature) {
        // 根據 SIG 標準，此特徵為 int16，單位為攝氏度，解析度為 0.01。
        // 所以需要將傳入的 float 值乘以 100。
        short tempValue = (short) (temperature * 100);
        ByteBuffer buffer = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort(tempValue);
        return buffer.array();
    }

    /**
     * 封裝 ESS 的濕度 (Humidity, 0x2A6F) 的值。
     * @param humidity 相對濕度，單位為 %。
     * @return 符合格式的 byte[]。
     */
    public static byte[] forHumidity(float humidity) {
        // 根據 SIG 標準，此特徵為 uint16，單位為百分比，解析度為 0.01。
        // 所以需要將傳入的 float 值乘以 100。
        int humidityValue = (int) (humidity * 100);
        ByteBuffer buffer = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) humidityValue);
        return buffer.array();
    }

    /**
     * 封裝 ESS 的氣壓 (Pressure, 0x2A6D) 的值。
     * @param pressure 氣壓，單位為百帕 (hPa)。
     * @return 符合格式的 byte[]。
     */
    public static byte[] forPressure(float pressure) {
        // 根據 SIG 標準，此特徵為 uint32，單位為帕斯卡(Pa)，解析度為 0.1。
        // 傳入值為 hPa (百帕)，所以需要先轉為 Pa (乘以100)，再乘以10得到 uint32 的值。
        int pressureValue = (int) (pressure * 100 * 10);
        ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(pressureValue);
        return buffer.array();
    }

    /**
     * 封裝 ESS 的風寒 (Wind Chill, 0x2A79) 的值。
     * @param temperature 體感溫度，單位為攝氏度。
     * @return 符合格式的 byte[]。
     */
    public static byte[] forWindChill(int temperature) {
        // 根據 SIG 標準，此特徵為 sint8，單位為攝氏度。
        return new byte[]{(byte) temperature};
    }


    /**
     * 封裝血氧飽和度單次測量 (PLX Spot-Check, 0x2A5E) 的值。
     * •PLX Spot-Check Measurement (血氧飽和度單次測量): 0x2A5E - 用於發送單次的測量數據，包括 SpO₂ (血氧飽和度百分比) 和脈率。這通常是一個 Notify (通知) 特徵。
     * •PLX Continuous Measurement (血氧飽和度連續測量): 0x2A5F - 用於連續發送測量數據。這也是一個 Notify 特徵。
     * •PLX Features (血氧飽和度功能): 0x2A60 - 唯讀 (Read)，描述設備支援的功能 (例如是否支援脈率、測量狀態等)。
     * @param spo2 血氧飽和度 (SFLOAT, 單位 %)。
     * @param pulseRate 脈率 (SFLOAT, 單位 bpm)。
     * @param includeTimestamp 是否包含時間戳。
     * @return 符合格式的 byte[]。
     */
    public static byte[] forPulseOximeterSpotCheck(float spo2, float pulseRate, boolean includeTimestamp) {
        // --- Flags (1 byte) ---
        // bit 0: Timestamp present
        // bit 1: Measurement Status present
        // bit 2: Device and Sensor Status present
        // bit 3: Pulse Amplitude Index present
        // 為了簡化，我們只實作時間戳
        byte flags = (byte) (includeTimestamp ? 0b0000_0001 : 0b0000_0000);

        // --- 計算緩衝區大小 ---
        int bufferSize = 1 + 4; // Flags(1) + SpO2(SFLOAT 2) + PulseRate(SFLOAT 2)
        if (includeTimestamp) {
            bufferSize += 7; // DateTime(7)
        }

        ByteBuffer buffer = ByteBuffer.allocate(bufferSize).order(ByteOrder.LITTLE_ENDIAN);

        // 1. Flags
        buffer.put(flags);

        // 2. SpO2 (SFLOAT) 和 Pulse Rate (SFLOAT)
        // SFLOAT 是一種16位元的浮點數。這裡為了簡化，我們直接將 float 轉為 short。
        buffer.putShort((short) spo2);
        buffer.putShort((short) pulseRate);

        // 3. Timestamp (DateTime) - 可選
        if (includeTimestamp) {
            Calendar now = Calendar.getInstance();
            buffer.putShort((short) now.get(Calendar.YEAR));
            buffer.put((byte) (now.get(Calendar.MONTH) + 1));
            buffer.put((byte) now.get(Calendar.DAY_OF_MONTH));
            buffer.put((byte) now.get(Calendar.HOUR_OF_DAY));
            buffer.put((byte) now.get(Calendar.MINUTE));
            buffer.put((byte) now.get(Calendar.SECOND));
        }

        return buffer.array();
    }


    /**
     * 封裝體重計量測 (0x2A9D) 的值。
     * @param weight 體重，單位為公斤(kg)。
     * @return 符合格式的 byte[]。
     */
    public static byte[] forWeightScaleMeasurement(float weight) {
        // Flags: 0b00000000 表示單位為公斤(kg)，不包含時間戳、使用者索引和BMI/身高。
        byte flags = 0b0000_0000;

        // Weight 是 UINT16 型別，解析度為 0.005 kg。
        // 所以需要將傳入的 float 值乘以 200 (1 / 0.005) 來得到 UINT16 的值。
        int weightValue = (int) (weight * 200);

        ByteBuffer buffer = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(flags);
        buffer.putShort((short) weightValue);

        return buffer.array();
    }

    /**
     * 生成設備信息數據包 (FE 36)
     * 這是連接後非常關鍵的一個數據包，用於告知 App 設備的基礎狀態。
     *
     * @param batteryLevel 當前電量 (0-100)
     * @param mcuVersion MCU 軟體版本 (e.g., 101 for "1.01")
     * @param bleVersion BLE 軟體版本 (e.g., 102 for "1.02")
     * @return 組裝好的 byte[]
     */
    public static byte[] forDeviceInfo_FE36(int batteryLevel, int mcuVersion, int bleVersion) {
        // 根據 spec: FE 36 電量(1) 設備狀態(1) MCU版本(2) BLE版本(2) UTC時間(4) 校驗和(1)
        ByteBuffer buffer = ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN);

        buffer.put((byte) 0xFE); // 標頭
        buffer.put((byte) 0x36); // 命令字

        buffer.put((byte) batteryLevel); // 電量
        buffer.put((byte) 0x00); // 設備狀態: 00 表示正常，非配網模式

        // MCU 版本 (2 bytes, little-endian)
        buffer.putShort((short) mcuVersion);
        // BLE 版本 (2 bytes, little-endian)
        buffer.putShort((short) bleVersion);

        // UTC 時間 (4 bytes, little-endian)
        long utcSeconds = Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis() / 1000L;
        buffer.putInt((int) utcSeconds);

        // 計算校驗和 (從 byte[1] 到 byte[11])
        byte checksum = 0;
        for (int i = 1; i < 12; i++) {
            checksum += buffer.get(i);
        }
        buffer.put(checksum);

        return buffer.array();
    }


    /**
     * 生成即時八電極阻抗數據包 (DF)
     *
     * @param weightKg 體重 (公斤)
     * @return 組裝好的 byte[]
     */
    public static byte[] forRealtimeImpedanceData_DF(float weightKg) {
        // 根據 spec: DF 體重(2) 穩定標誌(1) 保留(1) 阻抗1(2) ... 阻抗8(2) 校驗和(1)
        // 總長度: 1 + 2 + 1 + 1 + (8 * 2) + 1 = 22 bytes
        ByteBuffer buffer = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);

        buffer.put((byte) 0xDF); // 標頭

        // 體重: 單位 0.01kg, 所以乘以 100
        buffer.putShort((short)(weightKg * 100));

        buffer.put((byte) 0x01); // 0x01: 體重穩定標誌
        buffer.put((byte) 0x00); // 保留位

        // 模擬 8 個部位的阻抗值 (各 2 bytes)
        buffer.putShort((short) 500); // Z1: 左腿
        buffer.putShort((short) 505); // Z2: 右腿
        buffer.putShort((short) 480); // Z3: 軀幹
        buffer.putShort((short) 610); // Z4: 左臂
        buffer.putShort((short) 615); // Z5: 右臂
        buffer.putShort((short) 1100); // Z6: 左腿-左臂
        buffer.putShort((short) 1120); // Z7: 右腿-右臂
        buffer.putShort((short) 1000); // Z8: 左臂-右臂

        // 計算校驗和 (從 byte[1] 到 byte[20])
        byte checksum = 0;
        for (int i = 1; i < 21; i++) {
            checksum += buffer.get(i);
        }
        buffer.put(checksum);

        return buffer.array();
    }

    /**
     * 生成歷史數據記錄 (TLV 格式)
     * @return 組裝好的 byte[]
     */
    public static byte[] forHistoryTlvData() {
        // 模擬一條歷史記錄
        // TLV 格式: Type(1) Length(1) Value(N)
        // 記錄包含: 體重(0x01), 心率(0x0D), 8個阻抗(0x05-0x0C)

        // 數據包: F2 總長(1) 時間戳(4) [TLV1 TLV2 ...] 校驗和(1)

        // --- 創建 TLV Payloads ---
        // 1. 體重 TLV (Type 0x01, Length 2, Value: 65.50kg)
        byte[] weightTlv = new byte[]{(byte) 0x01, 0x02, (byte)0x96, (byte)0x19}; // 6550 -> 0x1996 -> 96 19
        // 2. 心率 TLV (Type 0x0D, Length 1, Value: 75bpm)
        byte[] heartRateTlv = new byte[]{(byte) 0x0D, 0x01, (byte)75};
        // 3. 八電極阻抗 TLVs (Type 0x05-0x0C, Length 2)
        byte[] z1_tlv = new byte[]{(byte)0x05, 0x02, (byte)0xF4, (byte)0x01}; // 500
        byte[] z2_tlv = new byte[]{(byte)0x06, 0x02, (byte)0xFA, (byte)0x01}; // 505
        // ... (可以繼續添加其他阻抗)

        // 合併所有 TLV
        ByteBuffer tlvBuffer = ByteBuffer.allocate(50); // 分配足夠空間
        tlvBuffer.put(weightTlv).put(heartRateTlv).put(z1_tlv).put(z2_tlv);
        byte[] allTlvs = new byte[tlvBuffer.position()];
        tlvBuffer.rewind();
        tlvBuffer.get(allTlvs);

        // --- 創建完整歷史數據包 ---
        int totalLength = 1 + 4 + allTlvs.length; // 總長欄位 + 時間戳 + TLV數據長度
        ByteBuffer finalBuffer = ByteBuffer.allocate(totalLength + 3).order(ByteOrder.LITTLE_ENDIAN); // F2 + 總長 + [payload] + 校驗和

        finalBuffer.put((byte) 0xF2); // 標頭
        finalBuffer.put((byte) totalLength); // Payload總長

        // 過去某個時間點的時間戳
        long pastTime = (System.currentTimeMillis() / 1000L) - (3600 * 24); // 模擬一天前的記錄
        finalBuffer.putInt((int) pastTime);

        finalBuffer.put(allTlvs); // 放入所有 TLV 數據

        // 計算校驗和 (從 byte[1] 到最後一個 payload byte)
        byte checksum = 0;
        for (int i = 1; i < finalBuffer.position(); i++) {
            checksum += finalBuffer.get(i);
        }
        finalBuffer.put(checksum);

        return finalBuffer.array();
    }

    /**
     * 生成一個通用的 ACK 包 (FD Sub-OpCode status)
     * 這是 App -> 設備方向的確認包，用於響應 App 的寫入操作。
     * @param subOpCode App 發送的子命令
     * @param status 0x00=成功, 0x01=失敗
     * @return 組裝好的 byte[]
     */
    public static byte[] forGenericAck(byte subOpCode, byte status) {
        // 格式: FD subOpCode status
        // 注意: 這個是簡化的 ACK，用於 F2 等指令的快速響應。
        // 與 MCU -> BLE 的 "55 FD ..." 格式不同。
        return new byte[]{(byte) 0xFD, subOpCode, status};
    }

    /**
     * 7.1/7.2/7.4 MCU -> BLE 通用響應 (55 FD SubOp raw_checksum AA)
     * 用於回應上電同步(5A)、時間同步(CC)、WIFI狀態(42)等指令
     * @param subOpCode 子命令碼，例如 0x5A
     * @return 組裝好的 byte[]
     */
    public static byte[] forGenericMcuResponse(byte subOpCode) {
        // 根據 spec: 55 FD SubOp raw_checksum AA
        // 此處的 raw_checksum 是 App 發來指令的校驗碼，我們這裡用 0x00 佔位
        return new byte[]{(byte) 0x55, (byte) 0xFD, subOpCode, (byte) 0x00, (byte) 0xAA};
    }
}
