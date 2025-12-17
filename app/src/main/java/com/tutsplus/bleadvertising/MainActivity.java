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
            final boolean includeTxPower = false;
            final boolean includeDeviceName = false;
            final String deviceName = bluetoothadapter.getName();
            final int appleCompanyId = 0x004C; // Apple's Company ID [1, 2]
            final byte[] manufacturerDataBytes = new byte[] { 0x12, 0x02, 0x00, 0x02 }; // 來自您圖片中 "Unnamed" 裝置的範例資料
            final ParcelUuid amsServiceUuid = new ParcelUuid(UUID.fromString("89D3502B-0F36-433A-8EF4-C502AD55F8DC"));
            final ParcelUuid serviceDataUuid = new ParcelUuid(UUID.fromString("0000FEA0-0000-1000-8000-00805f9b34fb"));
            final ParcelUuid batteryServiceUuid = new ParcelUuid(UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb"));
            final ParcelUuid deviceInfoServiceUuid = new ParcelUuid(UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb"));
            final ParcelUuid currentTimeServiceUuid = new ParcelUuid(UUID.fromString("00001805-0000-1000-8000-00805f9b34fb"));

            //準備要附加的資料。可以是空資料，或用來識別的特定位元組 這裡放一個簡單的標記，例如 0x01
            final byte[] serviceDataBytes = new byte[] { 0x01 };
            // --- 1. 設定廣播參數 ---
            // 保持可連接 (Connectable = true)，這樣掃描端才能請求 Scan Response
            final AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY) // 更改為低延遲模式以加快發現速度
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)    // 為了更好的訊號，使用高功率
                    .setConnectable(true)
                    .build();
            // --- 2. 建立主廣播封包 (Advertise Packet) ---
            // 這個封包放入 Manufacturer Data，Manufacturer Specific Data 必須放在主廣播封包 (Advertising Packet) 中才能被掃描 App 直接看到 並且不包含裝置名稱和 Tx Power 以節省空間
            final AdvertiseData data = new AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .setIncludeTxPowerLevel(false)
                    .addManufacturerData(appleCompanyId, manufacturerDataBytes)
                    // 使用 ServiceData 來廣播，它會包含一個短 UUID 和你的資料
                    .addServiceData(serviceDataUuid, serviceDataBytes)
                    .addServiceUuid(batteryServiceUuid) // 加入電池服務
                    .addServiceUuid(deviceInfoServiceUuid) // 加入裝置資訊服務
                    .addServiceUuid(currentTimeServiceUuid) // 加入當前時間服務
                    .build();
            // --- 3. 建立掃描回應封包 (Scan Response Packet) ---
            // 這裡放入完整的 128-bit UUID 和裝置名稱
            // 這樣組合通常不會超過 31 位元組 (除非你的裝置名稱非常長)
            final AdvertiseData response = new AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .addServiceUuid(amsServiceUuid) // 加入自定義的 AMS 服務
//                    .addServiceUuid(batteryServiceUuid) // 加入電池服務
//                    .addServiceUuid(deviceInfoServiceUuid) // 加入裝置資訊服務
//                    .addServiceUuid(currentTimeServiceUuid) // 加入當前時間服務
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
        Log.d("BLE_TEST", "--- Running New Calculation Tests ---");

        // --- 準備測試資料 (與 advertiseBtn 中的設定一致) ---
        final int appleCompanyId = 0x004C;
        final byte[] manufacturerDataBytes = new byte[] { 0x12, 0x02, 0x00, 0x02 };
        final ParcelUuid serviceDataUuid = new ParcelUuid(UUID.fromString("0000FEA0-0000-1000-8000-00805f9b34fb"));
        final byte[] serviceDataBytes = new byte[] { 0x01 };
        final ParcelUuid batteryServiceUuid = new ParcelUuid(UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb"));
        final ParcelUuid deviceInfoServiceUuid = new ParcelUuid(UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb"));
        final ParcelUuid currentTimeServiceUuid = new ParcelUuid(UUID.fromString("00001805-0000-1000-8000-00805f9b34fb"));
        final ParcelUuid amsServiceUuid = new ParcelUuid(UUID.fromString("89D3502B-0F36-433A-8EF4-C502AD55F8DC"));


        // --- 測試 1: 計算您目前 `data` 封包的大小 ---
        List<ParcelUuid> dataServiceUuids = new ArrayList<>();
        dataServiceUuids.add(batteryServiceUuid);
        dataServiceUuids.add(deviceInfoServiceUuid);
        dataServiceUuids.add(currentTimeServiceUuid);

        int dataPacketSize = calculateAdvertiseDataSize(
                false, // includeDeviceName
                false, // includeTxPower
                appleCompanyId, // manufacturerId
                manufacturerDataBytes, // manufacturerData
                dataServiceUuids, // serviceUuids
                serviceDataUuid, // serviceDataUuid
                serviceDataBytes // serviceData
        );
        /*
         * 預期計算:
         * Flags: 3 bytes
         * Manufacturer Data (Apple): 2(hdr) + 2(id) + 4(data) = 8 bytes
         * Service Data (FEA0): 2(hdr) + 2(uuid) + 1(data) = 5 bytes
         * Service UUIDs (16-bit list): 2(hdr) + 3 * 2(uuids) = 8 bytes
         * 總計: 3 + 8 + 5 + 8 = 24 bytes
         */
        Log.d("BLE_TEST", "Current 'data' packet size: " + dataPacketSize + " bytes (Expected: 24)");
        if (dataPacketSize > 31) {
            Log.e("BLE_TEST", "WARNING: 'data' packet is TOO LARGE!");
        }

        // --- 測試 2: 計算您目前 `response` 封包的大小 ---
        List<ParcelUuid> responseServiceUuids = new ArrayList<>();
        responseServiceUuids.add(amsServiceUuid);

        int responsePacketSize = calculateAdvertiseDataSize(
                false, // includeDeviceName
                false, // includeTxPower
                -1, // manufacturerId
                null, // manufacturerData
                responseServiceUuids, // serviceUuids
                null, // serviceDataUuid
                null // serviceData
        );
        /*
         * 預期計算:
         * Flags: 3 bytes
         * Service UUIDs (128-bit list): 2(hdr) + 1 * 16(uuid) = 18 bytes
         * 總計: 3 + 18 = 21 bytes
         */
        Log.d("BLE_TEST", "Current 'response' packet size: " + responsePacketSize + " bytes (Expected: 21)");
        if (responsePacketSize > 31) {
            Log.e("BLE_TEST", "WARNING: 'response' packet is TOO LARGE!");
        }

        // --- 測試 3: 模擬一個會超限的失敗案例 ---
        List<ParcelUuid> tooLargeServiceUuids = new ArrayList<>();
        tooLargeServiceUuids.add(amsServiceUuid);
        tooLargeServiceUuids.add(batteryServiceUuid);
        tooLargeServiceUuids.add(deviceInfoServiceUuid);
        tooLargeServiceUuids.add(currentTimeServiceUuid);

        int failedPacketSize = calculateAdvertiseDataSize(
                false, false, -1, null,
                tooLargeServiceUuids,
                null, null
        );
        /*
         * 預期計算:
         * Flags: 3 bytes
         * 128-bit List: 18 bytes
         * 16-bit List: 2 + 3*2 = 8 bytes
         * 總計: 3 + 18 + 8 = 29 bytes
         * 註: 雖然是 29，但這就是我們之前討論過會失敗的臨界情況。
         */
        Log.d("BLE_TEST", "Simulated 'failed' packet size: " + failedPacketSize + " bytes (Expected: 29)");
        if (failedPacketSize > 31) {
            Log.e("BLE_TEST", "As expected, the simulated packet is TOO LARGE!");
        } else {
            Log.w("BLE_TEST", "Simulated packet is " + failedPacketSize + " bytes. This is a critical size where silent failure can occur on some devices.");
        }
        Log.d("BLE_TEST", "--- End of New Calculation Tests ---");
    }

    /**
     * 精確預先計算 AdvertiseData 封包的最終大小。
     * 這個函式模擬 AdvertiseData.Builder 的打包行為，包括 Flags、多個 UUID 列表和 Service Data。
     *
     * @param includeDeviceName 是否包含裝置名稱。
     * @param includeTxPower    是否包含發射功率。
     * @param manufacturerId    廠商 ID，如果為 -1 則不加入。
     * @param manufacturerData  廠商特定資料。
     * @param serviceUuids      要廣播的服務 UUID 列表 (對應 addServiceUuid)。
     * @param serviceDataUuid   用於 Service Data 的 UUID (對應 addServiceData 的第一個參數)。
     * @param serviceData       要附加的 Service Data (對應 addServiceData 的第二個參數)。
     * @return 計算出的封包大小（位元組）。
     */
    private int calculateAdvertiseDataSize(
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
        if (includeDeviceName) {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            String deviceName = adapter.getName();
            if (deviceName != null && !deviceName.isEmpty()) {
                size += (2 + deviceName.getBytes(Charset.defaultCharset()).length);
            }
        }

        // 3. 發射功率 (AD Type 0x0A - Tx Power Level)
        if (includeTxPower) {
            size += 3; // 1 (Length) + 1 (Type) + 1 (Data)
        }

        // 4. 廠商特定資料 (AD Type 0xFF - Manufacturer Specific Data)
        if (manufacturerId != -1 && manufacturerData != null) {
            // 1 (Length) + 1 (Type) + 2 (Company ID) + data length
            size += (2 + 2 + manufacturerData.length);
        }

        // 5. Service UUID 列表 (AD Types 0x03, 0x07)
        // Builder 會將 UUID 分類成 16-bit 和 128-bit 兩個列表。
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
            // 計算 16-bit UUID 列表的大小
            if (!uuids16.isEmpty()) {
                // 1 (Length) + 1 (Type 0x03) + (N * 2 bytes)
                size += (2 + uuids16.size() * 2);
            }
            // 計算 128-bit UUID 列表的大小
            if (!uuids128.isEmpty()) {
                // 1 (Length) + 1 (Type 0x07) + (N * 16 bytes)
                size += (2 + uuids128.size() * 16);
            }
        }

        // 6. Service Data (AD Type 0x16 for 16-bit UUID, 0x21 for 128-bit UUID)
        if (serviceDataUuid != null && serviceData != null) {
            if (is16BitUuid(serviceDataUuid)) {
                // 1 (Length) + 1 (Type 0x16) + 2 (UUID) + data length
                size += (2 + 2 + serviceData.length);
            } else {
                // 1 (Length) + 1 (Type 0x21) + 16 (UUID) + data length
                size += (2 + 16 + serviceData.length);
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
