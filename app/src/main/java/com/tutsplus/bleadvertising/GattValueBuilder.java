package com.tutsplus.bleadvertising;

import android.bluetooth.BluetoothGattCharacteristic;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Calendar;

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
}
