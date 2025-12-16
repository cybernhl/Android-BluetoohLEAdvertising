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
            final BluetoothLeScanner mBluetoothLeScanner = bluetoothadapter.getBluetoothLeScanner();
            final List<ScanFilter> filters = new ArrayList<ScanFilter>();
            final ScanFilter filter = new ScanFilter.Builder()
                    .setServiceUuid(demouuid)
                    .build();
            filters.add(filter);

            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            ScanCallback mScanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    if (result == null
                            || result.getDevice() == null
                            || TextUtils.isEmpty(result.getDevice().getName()))
                        return;

                    StringBuilder builder = new StringBuilder(result.getDevice().getName());

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
            mBluetoothLeScanner.startScan(filters, settings, mScanCallback);

            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothLeScanner.stopScan(mScanCallback);
                }
            }, 10000);

        });
        binding.advertiseBtn.setOnClickListener(v -> {
            BluetoothLeAdvertiser advertiser = bluetoothadapter.getBluetoothLeAdvertiser();
            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(false)
                    .build();
            AdvertiseData data = new AdvertiseData.Builder()
                    .setIncludeDeviceName(true)
                    .addServiceUuid(demouuid)
                    .addServiceData(demouuid, "0".getBytes(Charset.forName("UTF-8")))
                    .build();

            AdvertiseCallback advertisingCallback = new AdvertiseCallback() {
                @Override
                public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                    super.onStartSuccess(settingsInEffect);
                }

                @Override
                public void onStartFailure(int errorCode) {
                    Log.e("BLE", "Advertising onStartFailure: " + errorCode);
                    super.onStartFailure(errorCode);
                }
            };

            advertiser.startAdvertising(settings, data, advertisingCallback);
        });
        if (!bluetoothadapter.isMultipleAdvertisementSupported()) {
            Toast.makeText(getBaseContext(), "Multiple advertisement not supported", Toast.LENGTH_SHORT).show();
            binding.advertiseBtn.setEnabled(false);
            binding.discoverBtn.setEnabled(false);
        }
    }
}
