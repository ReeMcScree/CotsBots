package com.cotsbots.navigation;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal ball-following app.
 * One screen: camera preview + BT status + Follow/Stop button + Test-move button.
 * Camera -> OpenCV ball detection -> BLE command to the robot. No Gemini, no chat.
 */
public class BallFollowActivity extends AppCompatActivity {

    private static final String TAG = "COTSBOTS";

    // DFRobot BLE UUIDs (RoMeo BLE / Bluno)
    private static final UUID BLE_SERVICE =
            UUID.fromString("0000dfb0-0000-1000-8000-00805f9b34fb");
    private static final UUID BLE_SERIAL_CHAR =
            UUID.fromString("0000dfb1-0000-1000-8000-00805f9b34fb");
    private static final UUID BLE_NOTIFY_DESCRIPTOR =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final String BLUNO_PASSWORD = "DFRobot";
    private static final String BLUNO_MAC = "00:11:22:33:44:55";

    private static final int PERMISSION_REQUEST = 100;
    private static final String[] PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    // UI
    private PreviewView previewView;
    private TextView tvStatus, tvBluetooth;
    private Button btnFollow, btnTest, btnReconnect;

    // Camera
    private ExecutorService cameraExecutor;

    // BLE
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic serialChar;
    private boolean bleConnected = false;
    private boolean scanning = false;
    private final Handler bleHandler = new Handler(Looper.getMainLooper());
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    // Ball following
    private final BallFollower ballFollower = new BallFollower();
    private boolean following = false;
    private long lastCmdTime = 0;
    private static final long CMD_INTERVAL_MS = 150;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ball_follow);

        if (!OpenCVLoader.initLocal()) {
            Toast.makeText(this, "OpenCV failed", Toast.LENGTH_LONG).show();
        }

        previewView = findViewById(R.id.previewView);
        tvStatus    = findViewById(R.id.tvStatus);
        tvBluetooth = findViewById(R.id.tvBluetooth);
        btnFollow   = findViewById(R.id.btnFollow);
        btnTest     = findViewById(R.id.btnTest);
        btnReconnect= findViewById(R.id.btnReconnect);

        cameraExecutor = Executors.newSingleThreadExecutor();

        BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bm != null) bluetoothAdapter = bm.getAdapter();

        btnFollow.setOnClickListener(v -> {
            following = !following;
            btnFollow.setText(following ? "STOP" : "FOLLOW BALL");
            if (!following) sendCommand("0,0,0");
            setStatus(following ? "Following ON" : "Following OFF");
        });

        btnTest.setOnClickListener(v -> {
            Log.d(TAG, "TEST: 200,200,1000");
            sendCommand("200,200,1000");
            setStatus("Test move sent");
        });

        btnReconnect.setOnClickListener(v -> {
            disconnectBLE();
            startBLEScan();
        });
        // Long-press reconnect = connect directly by MAC (bypass scanning)
        btnReconnect.setOnLongClickListener(v -> {
            disconnectBLE();
            connectByMac();
            return true;
        });

        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_REQUEST);
        } else {
            startCamera();
            startBLEScan();
        }
    }

    // ── Camera ───────────────────────────────────────────────────────────

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

                provider.unbindAll();
                provider.bindToLifecycle(this,
                        CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
                setStatus("Ready");
            } catch (Exception e) {
                Log.e(TAG, "Camera: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeFrame(ImageProxy image) {
        if (!following) { image.close(); return; }

        long now = System.currentTimeMillis();
        if (now - lastCmdTime < CMD_INTERVAL_MS) { image.close(); return; }

        try {
            Bitmap bmp = imageProxyToBitmap(image);
            if (bmp == null) { image.close(); return; }

            Mat frame = new Mat();
            Utils.bitmapToMat(bmp, frame);
            Imgproc.cvtColor(frame, frame, Imgproc.COLOR_RGBA2BGR);
            Core.rotate(frame, frame, Core.ROTATE_90_CLOCKWISE);

            BallFollower.Result result = ballFollower.analyze(frame);
            Log.d(TAG, "Ball: " + result.status + " -> " + result.command);
            sendCommand(result.command);
            lastCmdTime = now;

            final String status = result.status;
            uiHandler.post(() -> tvStatus.setText(status));
            frame.release();
        } catch (Exception e) {
            Log.e(TAG, "analyze: " + e.getMessage());
        } finally {
            image.close();
        }
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        try {
            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            java.nio.ByteBuffer yBuffer = planes[0].getBuffer();
            java.nio.ByteBuffer uBuffer = planes[1].getBuffer();
            java.nio.ByteBuffer vBuffer = planes[2].getBuffer();
            int ySize = yBuffer.remaining(), uSize = uBuffer.remaining(), vSize = vBuffer.remaining();
            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);
            android.graphics.YuvImage yuv = new android.graphics.YuvImage(
                    nv21, android.graphics.ImageFormat.NV21,
                    image.getWidth(), image.getHeight(), null);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            yuv.compressToJpeg(new android.graphics.Rect(
                    0, 0, image.getWidth(), image.getHeight()), 80, out);
            byte[] bytes = out.toByteArray();
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            Log.e(TAG, "toBitmap: " + e.getMessage());
            return null;
        }
    }

    // ── BLE ────────────────────────────────────────────────────────────────

    private void startBLEScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            uiHandler.post(() -> tvBluetooth.setText("BT: Enable Bluetooth")); return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) return;
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bleScanner == null) return;
        scanning = true;
        uiHandler.post(() -> tvBluetooth.setText("BT: Scanning..."));
        android.bluetooth.le.ScanSettings settings = new android.bluetooth.le.ScanSettings.Builder()
                .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(android.bluetooth.le.ScanSettings.MATCH_MODE_AGGRESSIVE)
                .build();
        bleScanner.startScan(null, settings, scanCallback);
        bleHandler.postDelayed(() -> {
            if (scanning) {
                stopScan();
                if (!bleConnected)
                    uiHandler.post(() -> tvBluetooth.setText("BT: Not found — tap ↺"));
            }
        }, 20000);
    }

    private void stopScan() {
        if (scanning && bleScanner != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED)
                bleScanner.stopScan(scanCallback);
            scanning = false;
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int type, ScanResult result) {
            if (ActivityCompat.checkSelfPermission(BallFollowActivity.this,
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
            BluetoothDevice device = result.getDevice();
            String name = device.getName();
            String advName = result.getScanRecord() != null
                    ? result.getScanRecord().getDeviceName() : null;
            String eff = name != null ? name : advName;
            StringBuilder uuidStr = new StringBuilder();
            if (result.getScanRecord() != null
                    && result.getScanRecord().getServiceUuids() != null) {
                for (android.os.ParcelUuid pu : result.getScanRecord().getServiceUuids())
                    uuidStr.append(pu.getUuid().toString()).append(" ");
            }
            Log.d(TAG, "BLE seen: name=[" + name + "] mac=[" + device.getAddress()
                    + "] uuids=[" + uuidStr.toString().trim() + "]");

            boolean hasDf = false;
            if (result.getScanRecord() != null
                    && result.getScanRecord().getServiceUuids() != null) {
                for (android.os.ParcelUuid pu : result.getScanRecord().getServiceUuids())
                    if (pu.getUuid().equals(BLE_SERVICE)) { hasDf = true; break; }
            }
            boolean nameMatch = eff != null &&
                    (eff.toLowerCase().contains("bluno") ||
                            eff.toLowerCase().contains("romeo") ||
                            eff.toLowerCase().contains("dfrobot") ||
                            eff.equals("4"));
            if (hasDf || nameMatch) {
                stopScan();
                final BluetoothDevice target = device;
                uiHandler.post(() -> tvBluetooth.setText("BT: Connecting..."));
                // Small delay after stopping scan improves connect reliability,
                // then connect with autoConnect=true (patient, like the Bluno app).
                bleHandler.postDelayed(() -> {
                    if (ActivityCompat.checkSelfPermission(BallFollowActivity.this,
                            Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
                    bluetoothGatt = target.connectGatt(
                            BallFollowActivity.this, true, gattCallback, BluetoothDevice.TRANSPORT_LE);
                }, 300);
            }
        }
        @Override public void onScanFailed(int error) {
            uiHandler.post(() -> tvBluetooth.setText("BT: Scan failed — tap ↺"));
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (ActivityCompat.checkSelfPermission(BallFollowActivity.this,
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;

            Log.d(TAG, "ConnState: status=" + status + " newState=" + newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                bleHandler.removeCallbacksAndMessages(null);
                uiHandler.post(() -> tvBluetooth.setText("BT: Connected..."));
                try { gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH); } catch (Exception ignored) {}
                // Give the stack a moment before discovering services.
                bleHandler.postDelayed(() -> {
                    if (ActivityCompat.checkSelfPermission(BallFollowActivity.this,
                            Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
                        gatt.discoverServices();
                }, 700);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                bleConnected = false; serialChar = null;
                // status 0 = clean disconnect; anything else = error, close and allow rescan.
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Connect error status=" + status + " — closing gatt");
                    try { gatt.close(); } catch (Exception ignored) {}
                    bluetoothGatt = null;
                    uiHandler.post(() -> tvBluetooth.setText("BT: Retry — tap ↺"));
                } else {
                    uiHandler.post(() -> tvBluetooth.setText("BT: Disconnected — tap ↺"));
                }
            }
        }
        @Override public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (ActivityCompat.checkSelfPermission(BallFollowActivity.this,
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
            BluetoothGattService svc = gatt.getService(BLE_SERVICE);
            if (svc != null) serialChar = svc.getCharacteristic(BLE_SERIAL_CHAR);
            if (serialChar == null) {
                for (BluetoothGattService s : gatt.getServices())
                    for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                        int p = c.getProperties();
                        if ((p & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                                (p & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                            serialChar = c; break;
                        }
                    }
            }
            if (serialChar != null) {
                if ((serialChar.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                    gatt.setCharacteristicNotification(serialChar, true);
                    BluetoothGattDescriptor d = serialChar.getDescriptor(BLE_NOTIFY_DESCRIPTOR);
                    if (d != null) {
                        d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(d);
                    }
                }
                bleConnected = true;
                bleHandler.postDelayed(() -> sendPassword(), 1000);
                uiHandler.post(() -> tvBluetooth.setText("BT: Connected ✓"));
            } else {
                uiHandler.post(() -> tvBluetooth.setText("BT: No serial — tap ↺"));
            }
        }
    };

    private void sendPassword() {
        if (!bleConnected || serialChar == null) return;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) return;
        serialChar.setValue((BLUNO_PASSWORD + "\r\n").getBytes(StandardCharsets.UTF_8));
        serialChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        bluetoothGatt.writeCharacteristic(serialChar);
    }

    private void sendCommand(String command) {
        if (!bleConnected || bluetoothGatt == null || serialChar == null) {
            Log.d(TAG, "BLE not ready — would send: " + command); return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) return;
        serialChar.setValue((command + "\n").getBytes(StandardCharsets.UTF_8));
        serialChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        bluetoothGatt.writeCharacteristic(serialChar);
        Log.d(TAG, "Sent: " + command);
    }

    // Connect directly to the board by MAC address, bypassing scanning.
    // This works like nRF reconnecting to a known device.
    private void connectByMac() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) return;
        try {
            stopScan();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(BLUNO_MAC);
            uiHandler.post(() -> tvBluetooth.setText("BT: Connecting (MAC)..."));
            Log.d(TAG, "Connecting directly to " + BLUNO_MAC);
            bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } catch (Exception e) {
            Log.e(TAG, "connectByMac: " + e.getMessage());
            uiHandler.post(() -> tvBluetooth.setText("BT: MAC connect failed"));
        }
    }

    private void disconnectBLE() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED && bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        bleConnected = false; serialChar = null;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void setStatus(String msg) { uiHandler.post(() -> tvStatus.setText(msg)); }

    private boolean hasPermissions() {
        for (String p : PERMISSIONS)
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                return false;
        return true;
    }

    @Override public void onRequestPermissionsResult(int code,
                                                     @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == PERMISSION_REQUEST) { startCamera(); startBLEScan(); }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        disconnectBLE();
    }
}