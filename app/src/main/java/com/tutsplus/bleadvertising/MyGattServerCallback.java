// 檔案路徑: com/tutsplus/bleadvertising/MyGattServerCallback.java
package com.tutsplus.bleadvertising;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import java.util.Arrays;
import java.util.HashSet;

public class MyGattServerCallback extends BluetoothGattServerCallback {

    // 在類別內部定義 TAG，不再依賴 Kotlin 檔案
    private static final String TAG = "MyGattServerCallback";

    private final ServicesManager servicesManager;
    private final BluetoothGattServer gattServer;
    private final HashSet<BluetoothDevice> connectedDevices = new HashSet<>();

    // --- 建構函式 (已修正) ---
    // 移除了不相關的 AdvertisingManager
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public MyGattServerCallback(ServicesManager servicesManager, BluetoothGattServer gattServer) {
        this.servicesManager = servicesManager;
        this.gattServer = gattServer;

        // 將 gattServer 實例和連線設備列表傳給 servicesManager，以便它能發送通知
        this.servicesManager.setGattServer(gattServer, connectedDevices);
    }

    @Override
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
        super.onConnectionStateChange(device, status, newState);

        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.d(TAG, "設備已連接: " + device.getAddress());
                connectedDevices.add(device);

                // 當有設備連接時，啟動所有模擬
                if (servicesManager != null) {
                    // 延遲啟動，確保客戶端已準備好接收通知
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        servicesManager.startSimulation();
                    }, 1000);
                }

            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.d(TAG, "設備已斷開: " + device.getAddress());
                connectedDevices.remove(device);
                // 確保在所有設備斷開後停止模擬
                if (connectedDevices.isEmpty() && servicesManager != null) {
                    servicesManager.stopSimulation();
                }
            }
        } else {
            Log.e(TAG, "onConnectionStateChange 收到錯誤: " + status + " for device " + device.getAddress());
            connectedDevices.remove(device);
            if (connectedDevices.isEmpty() && servicesManager != null) {
                servicesManager.stopSimulation();
            }
        }
    }

    @Override
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void onCharacteristicWriteRequest(BluetoothDevice device,
                                             int requestId,
                                             BluetoothGattCharacteristic characteristic,
                                             boolean preparedWrite, boolean responseNeeded,
                                             int offset, byte[] value) {
        super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
        Log.d(TAG, "onCharacteristicWriteRequest for " + characteristic.getUuid().toString() + " value: " + bytesToHex(value));

        // 處理 CF597 的寫入指令 (0xFFF1)
        if (ServicesManager.HEALTH_SCALE_C2_WRITE_UUID.equals(characteristic.getUuid())) {
            if (value != null && value.length > 0) {
                byte opCode = value[0];

                // 只關心歷史數據請求 (F2)
                if (opCode == (byte) 0xF2) {
                    Log.d(TAG, "收到歷史數據請求 (F2)，準備發送歷史數據...");

                    // 延遲一小段時間後，發送歷史數據
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        byte[] historyData = GattValueBuilder.forHistoryTlvData();
                        Log.d(TAG, "正在發送歷史數據: " + bytesToHex(historyData));

                        // 🔴 修正點：直接呼叫 ServicesManager 內部的 notifyCharacteristicChanged 方法
                        // 並且需要先找到對應的特徵 (Characteristic)
                        BluetoothGattService service = gattServer.getService(ServicesManager.HEALTH_SCALE_C2_SERVICE_UUID);
                        if (service != null) {
                            BluetoothGattCharacteristic notifyChar = service.getCharacteristic(ServicesManager.HEALTH_SCALE_C2_NOTIFY_UUID);
                            if (notifyChar != null) {
                                notifyChar.setValue(historyData);
                                servicesManager.notifyCharacteristicChanged(notifyChar, false);
                            } else {
                                Log.e(TAG, "錯誤: 找不到 0xFFF4 特徵來發送歷史數據。");
                            }
                        } else {
                            Log.e(TAG, "錯誤: 找不到 0xFFF0 服務。");
                        }

                    }, 500); // 延遲 500ms
                }
            }
        } else {
            // 處理其他特徵的寫入 (如 FTMS)
            // 🔴 修正點：servicesManager 中沒有 handleCharacteristicWrite 方法，所以直接註解掉。
            // 如果需要處理 FTMS 等其他服務的寫入，邏輯應該直接寫在這裡，
            // 或者在 ServicesManager 中提供一個真正存在的方法。
            // if (servicesManager != null) {
            //     servicesManager.handleCharacteristicWrite(characteristic, offset, value);
            // }
            Log.d(TAG, "收到對其他特徵的寫入請求，暫不處理: " + characteristic.getUuid());
        }

        // 告訴客戶端寫入操作已成功接收
        if (responseNeeded) {
            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
        }
    }


    @Override
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
        super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
        Log.d(TAG, "onDescriptorWriteRequest: " + descriptor.getUuid().toString() + " value: " + bytesToHex(value));

        // 當客戶端寫入 CCCD 來啟用/停用通知時
        if (ServicesManager.CCCD_UUID.equals(descriptor.getUuid())) {
            if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value)) {
                Log.d(TAG, "客戶端已啟用通知: " + descriptor.getCharacteristic().getUuid());
            } else if (Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value)) {
                Log.d(TAG, "客戶端已停用通知: " + descriptor.getCharacteristic().getUuid());
            }

            if (responseNeeded) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            }
        } else {
            if (responseNeeded) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, value);
            }
        }
    }

    @Override
    public void onServiceAdded(int status, BluetoothGattService service) {
        super.onServiceAdded(status, service);
        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "服務已成功添加: " + service.getUuid());
        } else {
            Log.e(TAG, "添加服務失敗，狀態: " + status + " UUID: " + service.getUuid());
        }
    }

    private String bytesToHex(byte[] bytes) {
        if (bytes == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}
