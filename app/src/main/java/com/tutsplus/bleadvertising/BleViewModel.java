package com.tutsplus.bleadvertising;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

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
