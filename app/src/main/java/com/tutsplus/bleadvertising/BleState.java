package com.tutsplus.bleadvertising;

/**
 * 定義藍牙操作的各種狀態
 */
public enum BleState {
    IDLE,          // 閒置狀態
    START_SCAN,          // 閒置狀態
    SCANNING,      // 正在掃描
    START_ADVERTISE,
    ADVERTISING,   // 正在廣播
    SCAN_STOPPING, // 正在請求停止掃描（由計時器觸發）
    FAILURE        // 發生錯誤
}

