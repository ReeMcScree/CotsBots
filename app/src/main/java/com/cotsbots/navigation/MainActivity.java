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
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONObject;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "COTSBOTS";

    // ⚠️ Replace with your Gemini API key from aistudio.google.com
    private static final String GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY;
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key="
                    + GEMINI_API_KEY;

    private static final String NAV_PROMPT =
            "You are a robot navigation assistant. Look at this image from a robot's front camera.\n" +
                    "GREEN box=FAR zone, YELLOW box=MID zone, RED box=CLOSE zone.\n" +
                    "Respond ONLY with JSON: {\"command\":\"FORWARD\",\"reason\":\"path clear\"}\n" +
                    "Commands: FORWARD, STOP, LEFT, RIGHT, BACK. Reason max 5 words.";

    // Move duration in milliseconds — robot moves for this long then stops
    private static final int MOVE_DURATION_MS = 1000;

    // Motor speed for AI-driven moves (0-255), used by wordToCommand()
    private static final int MOVE_SPEED = 200;

    // DFRobot Bluno BLE UUIDs
    private static final UUID BLE_SERVICE =
            UUID.fromString("0000dfb0-0000-1000-8000-00805f9b34fb");
    private static final UUID BLE_SERIAL_CHAR =
            UUID.fromString("0000dfb1-0000-1000-8000-00805f9b34fb");
    private static final UUID BLE_NOTIFY_DESCRIPTOR =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final String BLUNO_PASSWORD = "DFRobot";

    private static final int PERMISSION_REQUEST = 100;
    private static final String[] PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    // UI
    private PreviewView previewView;
    private ImageView ivProcessed;
    private EditText etPrompt;
    private Button btnCapture, btnSend, btnReconnect;
    private TextView tvCommand, tvReason, tvStatus, tvBluetooth, tvPixelInfo;
    private LinearLayout chatContainer;
    private ScrollView chatScrollView;
    private ProgressBar progressBar;

    // Camera
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;

    // BLE
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic serialChar;
    private boolean bleConnected = false;
    private boolean scanning = false;
    private boolean passwordSent = false;
    private final Handler bleHandler = new Handler(Looper.getMainLooper());

    // State
    private String capturedImageBase64 = null;
    private int lastDangerPixels = 0;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private long lastApiCallTime = 0;
    private static final long MIN_API_INTERVAL_MS = 6000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!OpenCVLoader.initLocal()) {
            Toast.makeText(this, "OpenCV failed", Toast.LENGTH_LONG).show();
        }

        previewView    = findViewById(R.id.previewView);
        ivProcessed    = findViewById(R.id.ivProcessed);
        etPrompt       = findViewById(R.id.etPrompt);
        btnCapture     = findViewById(R.id.btnCapture);
        btnSend        = findViewById(R.id.btnSend);
        btnReconnect   = findViewById(R.id.btnReconnect);
        tvCommand      = findViewById(R.id.tvCommand);
        tvReason       = findViewById(R.id.tvReason);
        tvStatus       = findViewById(R.id.tvStatus);
        tvBluetooth    = findViewById(R.id.tvBluetooth);
        tvPixelInfo    = findViewById(R.id.tvPixelInfo);
        chatContainer  = findViewById(R.id.chatContainer);
        chatScrollView = findViewById(R.id.chatScrollView);
        progressBar    = findViewById(R.id.progressBar);

        cameraExecutor = Executors.newSingleThreadExecutor();

        BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bm != null) bluetoothAdapter = bm.getAdapter();

        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_REQUEST);
        } else {
            startCamera();
            startBLEScan();
        }

        btnCapture.setOnClickListener(v -> captureAndProcess());
        btnSend.setOnClickListener(v -> sendManualMessage());
        btnSend.setEnabled(false);
        btnReconnect.setOnClickListener(v -> {
            disconnectBLE();
            startBLEScan();
        });
    }

    // ── Camera ──────────────────────────────────────────────────────────

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
                provider.unbindAll();
                provider.bindToLifecycle(this,
                        CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
                setStatus("Ready — tap Capture!");
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // ── BLE ──────────────────────────────────────────────────────────────

    private void startBLEScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            uiHandler.post(() -> tvBluetooth.setText("BT: Enable Bluetooth"));
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) return;

        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bleScanner == null) return;

        scanning = true;
        passwordSent = false;
        uiHandler.post(() -> tvBluetooth.setText("BT: Scanning..."));
        bleScanner.startScan(scanCallback);

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
        @Override
        public void onScanResult(int type, ScanResult result) {
            if (ActivityCompat.checkSelfPermission(MainActivity.this,
                    Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) return;

            BluetoothDevice device = result.getDevice();
            String name = device.getName();
            if (name != null) Log.d(TAG, "BLE: [" + name + "]");

            if (name != null && name.toLowerCase().contains("bluno")) {
                stopScan();
                uiHandler.post(() -> tvBluetooth.setText("BT: Connecting..."));
                bluetoothGatt = device.connectGatt(MainActivity.this, false,
                        gattCallback, BluetoothDevice.TRANSPORT_LE);
            }
        }

        @Override
        public void onScanFailed(int error) {
            uiHandler.post(() -> tvBluetooth.setText("BT: Scan failed — tap ↺"));
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (ActivityCompat.checkSelfPermission(MainActivity.this,
                    Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) return;

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                uiHandler.post(() -> tvBluetooth.setText("BT: Connected..."));
                bleHandler.postDelayed(() -> gatt.discoverServices(), 500);
            } else {
                bleConnected = false;
                serialChar = null;
                passwordSent = false;
                uiHandler.post(() -> tvBluetooth.setText("BT: Disconnected — tap ↺"));
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (ActivityCompat.checkSelfPermission(MainActivity.this,
                    Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) return;

            // Log all services
            for (BluetoothGattService s : gatt.getServices()) {
                Log.d(TAG, "SERVICE: " + s.getUuid());
                for (BluetoothGattCharacteristic c : s.getCharacteristics())
                    Log.d(TAG, "  CHAR: " + c.getUuid() + " props=" + c.getProperties());
            }

            // Try DFRobot service
            BluetoothGattService svc = gatt.getService(BLE_SERVICE);
            if (svc != null) {
                serialChar = svc.getCharacteristic(BLE_SERIAL_CHAR);
                Log.d(TAG, "Found DFRobot service!");
            }

            // Fallback — any writable char
            if (serialChar == null) {
                for (BluetoothGattService s : gatt.getServices()) {
                    for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                        int p = c.getProperties();
                        if ((p & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                                (p & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                            serialChar = c;
                            Log.d(TAG, "Fallback char: " + c.getUuid());
                            break;
                        }
                    }
                    if (serialChar != null) break;
                }
            }

            if (serialChar != null) {
                if ((serialChar.getProperties() &
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                    gatt.setCharacteristicNotification(serialChar, true);
                    BluetoothGattDescriptor desc =
                            serialChar.getDescriptor(BLE_NOTIFY_DESCRIPTOR);
                    if (desc != null) {
                        desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(desc);
                    }
                }
                bleConnected = true;
                String name = gatt.getDevice().getName();
                bleHandler.postDelayed(() -> sendPassword(), 1000);
                uiHandler.post(() -> tvBluetooth.setText("BT: " + name + " ✓"));
            } else {
                uiHandler.post(() -> tvBluetooth.setText("BT: No serial — tap ↺"));
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic ch, int status) {
            Log.d(TAG, "Write: " + (status == BluetoothGatt.GATT_SUCCESS ? "OK" : "FAIL"));
            if (status == BluetoothGatt.GATT_SUCCESS && !passwordSent) {
                passwordSent = true;
                Log.d(TAG, "Password sent OK");
            }
        }
    };

    private void sendPassword() {
        if (!bleConnected || serialChar == null) return;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) return;
        byte[] pw = (BLUNO_PASSWORD + "\r\n").getBytes(StandardCharsets.UTF_8);
        serialChar.setValue(pw);
        serialChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        bluetoothGatt.writeCharacteristic(serialChar);
        Log.d(TAG, "Sending password: " + BLUNO_PASSWORD);
    }

    // ── Send Command ──────────────────────────────────────────────────────
    // Sends lowercase command with duration e.g. "w1000\n" = forward 1 second
    // Robot firmware: w=forward, s=backward, a=left, d=right

    private void sendCommand(String command) {
        if (!bleConnected || bluetoothGatt == null || serialChar == null) {
            Log.d(TAG, "BLE not ready — would send: " + command);
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) return;

        String cmd = command + "\n";
        serialChar.setValue(cmd.getBytes(StandardCharsets.UTF_8));
        serialChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        bluetoothGatt.writeCharacteristic(serialChar);
        Log.d(TAG, "Sent command: " + command);
    }

    private void disconnectBLE() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
            if (bluetoothGatt != null) {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
                bluetoothGatt = null;
            }
        }
        bleConnected = false;
        serialChar = null;
        passwordSent = false;
    }

    // ── Capture ──────────────────────────────────────────────────────────

    private void captureAndProcess() {
        if (imageCapture == null) return;
        long now = System.currentTimeMillis();
        if (now - lastApiCallTime < MIN_API_INTERVAL_MS) {
            setStatus("Wait " + ((MIN_API_INTERVAL_MS - (now - lastApiCallTime)) / 1000 + 1) + "s");
            return;
        }
        setStatus("Capturing...");
        btnCapture.setEnabled(false);
        btnSend.setEnabled(false);

        File photoFile = new File(getCacheDir(), "capture.jpg");
        imageCapture.takePicture(
                new ImageCapture.OutputFileOptions.Builder(photoFile).build(),
                cameraExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults o) {
                        processWithOpenCV(photoFile);
                    }
                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        setStatus("Capture error");
                        uiHandler.post(() -> btnCapture.setEnabled(true));
                    }
                });
    }

    // ── OpenCV ───────────────────────────────────────────────────────────

    private void processWithOpenCV(File imageFile) {
        setStatus("Processing...");
        Bitmap original = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
        Mat frame = new Mat();
        Utils.bitmapToMat(original, frame);
        Core.rotate(frame, frame, Core.ROTATE_90_CLOCKWISE);

        int h = frame.rows(), w = frame.cols();
        Mat gray = new Mat(), blurred = new Mat(), edges = new Mat();
        Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.GaussianBlur(gray, blurred, new Size(5, 5), 0);
        Imgproc.Canny(blurred, edges, 50, 150);

        int danger = Core.countNonZero(new Mat(edges, new Rect(0, (int)(h*.66), w, (int)(h*.34))));
        int mid    = Core.countNonZero(new Mat(edges, new Rect(0, (int)(h*.33), w, (int)(h*.33))));
        int far    = Core.countNonZero(new Mat(edges, new Rect(0, 0,            w, (int)(h*.33))));
        lastDangerPixels = danger;

        Imgproc.rectangle(frame, new Point(0,0), new Point(w,h*.33),
                new Scalar(0,255,0), 5);
        Imgproc.putText(frame, "FAR:"+far, new Point(15,(int)(h*.15)),
                Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, new Scalar(0,255,0), 3);
        Imgproc.rectangle(frame, new Point(0,h*.33), new Point(w,h*.66),
                new Scalar(0,255,255), 5);
        Imgproc.putText(frame, "MID:"+mid, new Point(15,(int)(h*.52)),
                Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, new Scalar(0,255,255), 3);
        Imgproc.rectangle(frame, new Point(0,h*.66), new Point(w,h),
                new Scalar(0,0,255), 5);
        Imgproc.putText(frame, "CLOSE:"+danger, new Point(15,(int)(h*.88)),
                Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, new Scalar(0,0,255), 3);

        Bitmap bmp = Bitmap.createBitmap(frame.cols(), frame.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(frame, bmp);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        capturedImageBase64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

        uiHandler.post(() -> {
            ivProcessed.setImageBitmap(bmp);
            ivProcessed.setVisibility(View.VISIBLE);
            previewView.setVisibility(View.GONE);
            tvPixelInfo.setText("🟢 Far:"+far+"  🟡 Mid:"+mid+"  🔴 Close:"+danger);
            btnCapture.setEnabled(true);
            btnSend.setEnabled(true);
        });

        // Immediate local STOP if very close
        if (danger > 150000) {
            sendCommand("0,0,0"); // stop
            updateCommandUI("STOP", "Obstacle very close");
            return;
        }

        sendToGemini(NAV_PROMPT, true);
    }

    // ── Gemini ───────────────────────────────────────────────────────────

    private void sendToGemini(String prompt, boolean isNav) {
        if (capturedImageBase64 == null) return;
        lastApiCallTime = System.currentTimeMillis();
        uiHandler.post(() -> progressBar.setVisibility(View.VISIBLE));
        setStatus(isNav ? "Asking Gemini..." : "Sending...");

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Thread.sleep(2000);

                JSONObject textPart = new JSONObject();
                textPart.put("text", prompt + "\nDanger pixels: " + lastDangerPixels);
                JSONObject inlineData = new JSONObject();
                inlineData.put("mime_type", "image/jpeg");
                inlineData.put("data", capturedImageBase64);
                JSONObject imagePart = new JSONObject();
                imagePart.put("inline_data", inlineData);
                JSONArray parts = new JSONArray();
                parts.put(textPart); parts.put(imagePart);
                JSONObject content = new JSONObject();
                content.put("parts", parts); content.put("role", "user");
                JSONArray contents = new JSONArray();
                contents.put(content);
                JSONObject genConfig = new JSONObject();
                genConfig.put("temperature", 0.1);
                genConfig.put("maxOutputTokens", isNav ? 200 : 1000);
                JSONObject body = new JSONObject();
                body.put("contents", contents);
                body.put("generationConfig", genConfig);

                URL url = new URL(GEMINI_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(20000);
                conn.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));

                int code = conn.getResponseCode();
                if (code == 200) {
                    String rs = new String(readStream(conn.getInputStream()), StandardCharsets.UTF_8);
                    JSONObject resp = new JSONObject(rs);
                    String text = resp.getJSONArray("candidates")
                            .getJSONObject(0).getJSONObject("content")
                            .getJSONArray("parts").getJSONObject(0)
                            .getString("text").trim();

                    if (isNav) {
                        String clean = text.replace("```json","").replace("```","").trim();
                        if (!clean.endsWith("}")) clean += "\"}";
                        try {
                            JSONObject p = new JSONObject(clean);
                            String cmd = p.optString("command","STOP");
                            String reason = p.optString("reason","");
                            if (!cmd.equals("FORWARD") && !cmd.equals("STOP") &&
                                    !cmd.equals("LEFT") && !cmd.equals("RIGHT") &&
                                    !cmd.equals("BACK")) cmd = "STOP";
                            sendCommand(wordToCommand(cmd));
                            updateCommandUI(cmd, reason);
                            addChatMessage("Robot", "▶ " + cmd + " — " + reason, true);
                            setStatus("Command: " + cmd);
                        } catch (Exception e) {
                            Log.e(TAG, "Parse error on: " + clean);
                            // Extract command from raw text as fallback
                            String cmd = "STOP";
                            if (text.contains("FORWARD")) cmd = "FORWARD";
                            else if (text.contains("LEFT")) cmd = "LEFT";
                            else if (text.contains("RIGHT")) cmd = "RIGHT";
                            else if (text.contains("BACK")) cmd = "BACK";
                            sendCommand(wordToCommand(cmd));
                            updateCommandUI(cmd, "extracted");
                            addChatMessage("Robot", "▶ " + cmd, true);
                        }
                    } else {
                        addChatMessage("Gemini", text, true);
                        setStatus("Ready");
                    }
                } else if (code == 429) {
                    setStatus("Rate limited — wait 15s");
                    if (isNav) sendCommand("0,0,0");
                } else if (code == 503) {
                    Thread.sleep(5000);
                    sendToGemini(prompt, isNav);
                    return;
                } else {
                    setStatus("AI Error: " + code);
                    if (isNav) sendCommand("0,0,0");
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Gemini: " + e.getMessage());
                setStatus("Error: " + e.getMessage());
                if (isNav) sendCommand("0,0,0");
            }
            uiHandler.post(() -> {
                progressBar.setVisibility(View.GONE);
                btnSend.setEnabled(true);
            });
        });
    }

    private void sendManualMessage() {
        if (capturedImageBase64 == null) {
            Toast.makeText(this, "Capture image first!", Toast.LENGTH_SHORT).show();
            return;
        }
        String msg = etPrompt.getText().toString().trim();
        if (msg.isEmpty()) {
            Toast.makeText(this, "Type a message!", Toast.LENGTH_SHORT).show();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastApiCallTime < MIN_API_INTERVAL_MS) {
            Toast.makeText(this, "Wait a moment...", Toast.LENGTH_SHORT).show();
            return;
        }
        addChatMessage("You", msg, false);
        etPrompt.setText("");
        btnSend.setEnabled(false);
        sendToGemini(msg, false);
    }

    // ── Chat ─────────────────────────────────────────────────────────────

    private void addChatMessage(String sender, String message, boolean isGemini) {
        uiHandler.post(() -> {
            TextView label = new TextView(this);
            label.setText(sender);
            label.setTextColor(isGemini ? 0xFF9B6DFF : 0xFFAAAAAA);
            label.setTextSize(10);
            label.setPadding(12, 8, 12, 2);

            TextView bubble = new TextView(this);
            bubble.setText(message);
            bubble.setTextColor(0xFFFFFFFF);
            bubble.setTextSize(13);
            bubble.setLineSpacing(4, 1);
            bubble.setBackgroundColor(isGemini ? 0xFF1A1035 : 0xFF221540);
            bubble.setPadding(12, 8, 12, 10);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(8, 0, 8, 6);
            bubble.setLayoutParams(lp);

            chatContainer.addView(label);
            chatContainer.addView(bubble);
            chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    // Maps AI command word to robot firmware protocol: "left,right,duration"
    // Speeds -255..255 (negative = reverse). duration in ms, firmware auto-stops.
    // "0,0,0" = stop immediately. Firmware parser is whitespace-tolerant.
    private String wordToCommand(String word) {
        int spd = MOVE_SPEED;          // cruising speed 0-255
        int dur = MOVE_DURATION_MS;    // move duration in ms
        switch (word) {
            case "FORWARD": return  spd + "," +  spd + "," + dur;   // both forward
            case "BACK":    return -spd + "," + -spd + "," + dur;   // both reverse
            case "LEFT":    return -spd + "," +  spd + "," + dur;   // pivot left
            case "RIGHT":   return  spd + "," + -spd + "," + dur;   // pivot right
            default:        return "0,0,0";                          // STOP
        }
    }

    private void updateCommandUI(String command, String reason) {
        uiHandler.post(() -> {
            tvCommand.setText(command);
            tvReason.setText(reason);
            int color;
            switch (command) {
                case "FORWARD": color = 0xFF00FF88; break;
                case "LEFT":
                case "RIGHT":   color = 0xFF00BFFF; break;
                case "BACK":    color = 0xFFFF6B35; break;
                case "STOP":    color = 0xFFFF3366; break;
                default:        color = 0xFFFFFFFF; break;
            }
            tvCommand.setTextColor(color);
        });
    }

    private void setStatus(String msg) {
        uiHandler.post(() -> tvStatus.setText(msg));
    }

    private byte[] readStream(InputStream is) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = is.read(chunk)) != -1) buf.write(chunk, 0, n);
        return buf.toByteArray();
    }

    private boolean hasPermissions() {
        for (String p : PERMISSIONS)
            if (ContextCompat.checkSelfPermission(this, p)
                    != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int code,
                                           @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == PERMISSION_REQUEST) { startCamera(); startBLEScan(); }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        disconnectBLE();
    }
}