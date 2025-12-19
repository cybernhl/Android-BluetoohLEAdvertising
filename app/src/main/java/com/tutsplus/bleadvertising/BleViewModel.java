package com.tutsplus.bleadvertising;

import static com.tutsplus.bleadvertising.BleState.SCANNING;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BleViewModel extends ViewModel {

    private static final String TAG = "BleViewModel";

    // --- GATT 伺服器相關物件 (由外部注入和管理) ---
    private BluetoothGattServer gattServer;
    private final HashSet<BluetoothDevice> connectedDevices = new HashSet<>();

    // --- 狀態 LiveData ---
    private final MutableLiveData<BleState> _bleState = new MutableLiveData<>(BleState.IDLE);
    public final LiveData<BleState> bleState = _bleState;

    // --- UI 顯示資料 LiveData ---
    private final MutableLiveData<String> _scanResultText = new MutableLiveData<>("");
    public final LiveData<String> scanResultText = _scanResultText;

    private final MutableLiveData<String> _toastMessage = new MutableLiveData<>();
    public final LiveData<String> toastMessage = _toastMessage;

    /**
     * 核心方法：從外部 (Activity) 接收一個已初始化的 GATT 伺服器，並完成設定。
     * 這個方法只應該被呼叫一次。
     * @param server 已開啟並加入服務的 BluetoothGattServer 實例。
     */
    public void setupGattServerLogic(@NonNull BluetoothGattServer server) {
        if (this.gattServer != null) {
            Log.w(TAG, "GattServer 已經被設定過了，忽略新的設定。");
            return;
        }
        this.gattServer = server;
        this.connectedDevices.clear(); // 確保開始時是乾淨的狀態
        Log.i(TAG, "GATT Server 已經成功注入到 ViewModel。");
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void addGattService(BluetoothGattService service) {
        if (this.gattServer == null) {
            Log.e(TAG, "GattServer must be set before adding a service.");
            postToastMessage("錯誤：GattServer 尚未初始化");
            return;
        }
        try {
            this.gattServer.addService(service);
            Log.i(TAG, "成功加入服務: " + service.getUuid());
        } catch (SecurityException e) {
            Log.e(TAG, "加入服務失敗，缺少權限: " + service.getUuid(), e);
        }
    }

    /**
     * 讓 Activity 可以將 GATT 相關的回呼事件轉發給 ViewModel 處理。
     * @return 一個 BluetoothGattServerCallback 實例，供 Activity 在建立 GattServer 時使用。
     */
    public BluetoothGattServerCallback getGattServerCallback() {
        return gattServerCallback;
    }

    // 將 Callback 實作放在 ViewModel 中，這是處理邏輯的地方
    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    connectedDevices.add(device);
                    Log.i(TAG, "裝置已連接: " + device.getAddress() + " | 目前連線數: " + connectedDevices.size());
                    postToastMessage("裝置已連接: " + device.getAddress());
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    connectedDevices.remove(device);
                    Log.i(TAG, "裝置已斷線: " + device.getAddress() + " | 目前連線數: " + connectedDevices.size());
                    postToastMessage("裝置已斷線");
                }
            } else {
                connectedDevices.remove(device);
                Log.e(TAG, "連接時發生錯誤，狀態碼: " + status);
            }
        }

        @Override
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "收到讀取請求: " + characteristic.getUuid());
            if (gattServer == null) return; // 保護機制

            if (offset != 0) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, 0, null);
                return;
            }
            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, characteristic.getValue());
        }

        @Override
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            Log.d(TAG, "收到描述符寫入請求: " + descriptor.getUuid() + ", 值: " + Arrays.toString(value));
            if (gattServer == null) return; // 保護機制

            int status = BluetoothGatt.GATT_SUCCESS;
            // 這裡只處理 CCCD，您也可以擴充處理其他描述符
            if (descriptor.getUuid().toString().equalsIgnoreCase("00002902-0000-1000-8000-00805f9b34fb")) {
                if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    descriptor.setValue(value);
                    postToastMessage("通知已啟用");
                } else if (Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                    descriptor.setValue(value);
                    postToastMessage("通知已停用");
                } else {
                    status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
                }
            }

            if (responseNeeded) {
                gattServer.sendResponse(device, requestId, status, 0, null);
            }
        }
    };

    public void updateState(BleState newState) {
        if(newState==SCANNING){
            final long SCAN_PERIOD_SECONDS = 10;
            new Thread(() -> {
                try {
                    Log.d("BleViewModel", "Scan timer thread started, will stop in " + SCAN_PERIOD_SECONDS + " seconds.");
                    boolean finished = new CountDownLatch(1).await(SCAN_PERIOD_SECONDS, TimeUnit.SECONDS);
                    if (!finished  ) {
                        Log.d("BleViewModel", "Scan timer finished. Requesting scan stop.");
                        updateState(BleState.SCAN_STOPPING);
                    }
                } catch (InterruptedException e) {
                    Log.w("BleViewModel", "Scan timer thread interrupted.", e);
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
        if (_bleState.getValue() != newState) {
            _bleState.postValue(newState);
        }
    }

    public void setScanResultText(String text) {
        _scanResultText.postValue(text);
    }

    public void postToastMessage(String message) {
        _toastMessage.postValue(message);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (this.gattServer != null) {
            Log.d(TAG, "ViewModel onCleared : GATT Server  ");
            try {
                gattServer.close();
            } catch (SecurityException e) {
                Log.e(TAG, "關閉 GATT 伺服器失敗，缺少權限", e);
            }
            this.gattServer = null; // 清除引用
            this.connectedDevices.clear();
        }
    }
}
