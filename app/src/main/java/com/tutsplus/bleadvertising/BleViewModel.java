package com.tutsplus.bleadvertising;

import static com.tutsplus.bleadvertising.BleState.SCANNING;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BleViewModel extends ViewModel {

    // --- 狀態 LiveData ---
    private final MutableLiveData<BleState> _bleState = new MutableLiveData<>(BleState.IDLE);
    public final LiveData<BleState> bleState = _bleState;

    // --- UI 顯示資料 LiveData ---
    private final MutableLiveData<String> _scanResultText = new MutableLiveData<>("");
    public final LiveData<String> scanResultText = _scanResultText;

    private final MutableLiveData<String> _toastMessage = new MutableLiveData<>();
    public final LiveData<String> toastMessage = _toastMessage;

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
}
