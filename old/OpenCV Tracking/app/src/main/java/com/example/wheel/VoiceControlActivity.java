package com.example.wheel;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static com.example.wheel.BtThread.ConnectThread.bluetoothSocket;
import com.example.wheel.BtThread.ConnectedThread;

public class VoiceControlActivity extends AppCompatActivity {

    private static final String TAG = "VoiceControl";

    private static final String OPENAI_API_KEY = "REPLACE_WITH_YOUR_KEY";


    private enum VoiceState { LISTENING, EXECUTING, TRACKING }
    private VoiceState state = VoiceState.LISTENING;

    private TextView tvStatus;
    private TextView tvLog;
    private View waveView;


    private ActivityResultLauncher<Intent> speechLauncher;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isListeningLoopEnabled = true;

    private long wakeUpExpireTime = 0L;
    private static final long WAKE_WINDOW_MS = 8000L;

    private ConnectedThread btThread;

    private final OkHttpClient http = new OkHttpClient();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.acticity_voicecontrol);

        tvStatus = findViewById(R.id.tvStatus);
        tvLog = findViewById(R.id.tvLog);
        waveView = findViewById(R.id.waveView);

        startWavePulseAnimation(waveView);

        setupBluetoothThread();

        speechLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::onSpeechResult
        );


        setState(VoiceState.LISTENING, "Listening… (say: \"Hey Eric\")");
        startListeningLoop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isListeningLoopEnabled) {
            setState(state == VoiceState.TRACKING ? VoiceState.TRACKING : VoiceState.LISTENING,
                    state == VoiceState.TRACKING ? "Tracking… (say: \"stop following\")" : "Listening… (say: \"Hey Eric\")");
            mainHandler.postDelayed(this::startListeningLoop, 250);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isListeningLoopEnabled = false;
    }


    private void startListeningLoop() {
        if (!isListeningLoopEnabled) return;

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak…");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        try {
            speechLauncher.launch(intent);
        } catch (Exception e) {
            logLine("Speech launch failed: " + e.getMessage());
            mainHandler.postDelayed(this::startListeningLoop, 1000);
        }
    }

    private void onSpeechResult(ActivityResult result) {
        try {
            if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                mainHandler.postDelayed(this::startListeningLoop, 150);
                return;
            }

            ArrayList<String> list = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (list == null || list.isEmpty()) {
                mainHandler.postDelayed(this::startListeningLoop, 150);
                return;
            }

            String text = list.get(0);
            if (text == null) text = "";
            String lower = text.toLowerCase(Locale.ROOT).trim();

            logLine("You: " + text);

            long now = System.currentTimeMillis();

            if (lower.contains("stop following") || lower.contains("stop tracking") || lower.equals("stop")) {
                handleStopFollowing();
                mainHandler.postDelayed(this::startListeningLoop, 150);
                return;
            }

            if (lower.contains("hey eric")) {
                wakeUpExpireTime = now + WAKE_WINDOW_MS;
            }

            if (now > wakeUpExpireTime) {
                setState(VoiceState.LISTENING, "Listening… (say: \"Hey Eric\")");
                mainHandler.postDelayed(this::startListeningLoop, 150);
                return;
            }

            setState(state == VoiceState.TRACKING ? VoiceState.TRACKING : VoiceState.EXECUTING, "Thinking…");
            callChatGPTParse(lower);

        } catch (Exception e) {
            logLine("SpeechResult error: " + e.getMessage());
        } finally {
            mainHandler.postDelayed(this::startListeningLoop, 300);
        }
    }

    private void callChatGPTParse(String userText) {
        new Thread(() -> {
            try {
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("model", "gpt-4o-mini");
                jsonBody.put("temperature", 0.0);

                JSONObject sys = new JSONObject();
                sys.put("role", "system");
                sys.put("content",
                        "You are a robot command parser. Return ONLY a single JSON object, no code fences, no explanation.\n" +
                                "If the input does NOT contain the wake word 'hey eric' (case-insensitive), return: {\"type\":\"IGNORE\"}.\n" +
                                "Commands:\n" +
                                "1) MOVE: if user indicates forward/backward/left/right OR w/a/s/d, return:\n" +
                                "   {\"type\":\"MOVE\",\"command\":\"W|A|S|D\",\"duration\":<milliseconds>}.\n" +
                                "   duration: support 'ms' or 's' (seconds*1000). If missing, default 500.\n" +
                                "2) TRACK: if user indicates track/follow/tail/trace + a color, return:\n" +
                                "   {\"type\":\"TRACK\",\"color\":\"GREEN|RED|BLUE|YELLOW|BLACK|WHITE|ORANGE|PURPLE\"}.\n" +
                                "3) STOP: if user indicates stop following/stop tracking, return: {\"type\":\"STOP\"}.\n" +
                                "If unclear, return {\"type\":\"IGNORE\"}."
                );

                JSONObject user = new JSONObject();
                user.put("role", "user");
                user.put("content", userText);

                jsonBody.put("messages", new org.json.JSONArray().put(sys).put(user));

                RequestBody body = RequestBody.create(jsonBody.toString(), JSON);

                Request req = new Request.Builder()
                        .url("https://api.openai.com/v1/chat/completions")
                        .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                        .post(body)
                        .build();

                Response resp = http.newCall(req).execute();
                String respStr = resp.body() != null ? resp.body().string() : "";

                Log.d(TAG, respStr);

                JSONObject root = new JSONObject(respStr);
                String content = root.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");

                String json = extractFirstJsonObject(content);
                if (json == null) json = "{\"type\":\"IGNORE\"}";

                JSONObject cmd = new JSONObject(json);

                mainHandler.post(() -> dispatchCommand(cmd));

            } catch (Exception e) {
                mainHandler.post(() -> {
                    logLine("GPT error: " + e.getMessage());
                    setState(VoiceState.LISTENING, "Listening… (say: \"Hey Eric\")");
                });
            }
        }).start();
    }


    private void dispatchCommand(JSONObject cmd) {
        try {
            String type = cmd.optString("type", "IGNORE").toUpperCase(Locale.ROOT);

            switch (type) {
                case "MOVE":
                    handleMove(cmd);
                    break;

                case "TRACK":
                    handleTrack(cmd);
                    break;

                case "STOP":
                    handleStopFollowing();
                    break;

                default:
                    setState(state == VoiceState.TRACKING ? VoiceState.TRACKING : VoiceState.LISTENING,
                            state == VoiceState.TRACKING ? "Tracking… (say: \"stop following\")" : "Listening… (say: \"Hey Eric\")");
                    logLine("Parsed: " + cmd.toString());
                    break;
            }
        } catch (Exception e) {
            logLine("dispatch error: " + e.getMessage());
            setState(VoiceState.LISTENING, "Listening… (say: \"Hey Eric\")");
        }
    }

    private void handleMove(JSONObject cmd) {
        String command = cmd.optString("command", "").toUpperCase(Locale.ROOT);
        int duration = cmd.optInt("duration", 500);
        if (duration < 0) duration = 0;

        String c;
        switch (command) {
            case "W": c = "w"; break;
            case "A": c = "a"; break;
            case "S": c = "s"; break;
            case "D": c = "d"; break;
            default:
                logLine("MOVE invalid command: " + command);
                setState(VoiceState.LISTENING, "Listening… (say: \"Hey Eric\")");
                return;
        }

        setState(VoiceState.EXECUTING, "Executing MOVE " + command + " for " + duration + "ms");
        logLine("Parsed: " + cmd.toString());

        if (btThread == null) {
            logLine("Bluetooth not connected.");
            setState(VoiceState.LISTENING, "Listening… (connect Bluetooth first)");
            return;
        }

        btThread.write(c);

        mainHandler.postDelayed(() -> {
            if (btThread != null) btThread.write("x");
            setState(state == VoiceState.TRACKING ? VoiceState.TRACKING : VoiceState.LISTENING,
                    state == VoiceState.TRACKING ? "Tracking… (say: \"stop following\")" : "Listening… (say: \"Hey Eric\")");
        }, duration);
    }

    private void handleTrack(JSONObject cmd) {
        String color = cmd.optString("color", "GREEN").toUpperCase(Locale.ROOT);

        setState(VoiceState.TRACKING, "Tracking " + color + "… (say: \"stop following\")");
        logLine("Parsed: " + cmd.toString());

        Intent intent = new Intent(this, ColorBlobDetectionActivity.class);
        intent.putExtra("color", color);
        startActivity(intent);
    }

    private void handleStopFollowing() {
        sendBroadcast(new Intent("STOP_TRACKING"));
        if (btThread != null) btThread.write("x");

        setState(VoiceState.LISTENING, "Listening… (say: \"Hey Eric\")");
        logLine("Stop following.");
    }

    private void setupBluetoothThread() {
        try {
            if (bluetoothSocket != null && bluetoothSocket.isConnected()) {
                btThread = new ConnectedThread(bluetoothSocket);
                btThread.start();
                logLine("Bluetooth thread ready.");
            } else {
                logLine("Bluetooth not connected yet (open BluetoothActivity first).");
            }
        } catch (Exception e) {
            logLine("Bluetooth setup error: " + e.getMessage());
        }
    }

    private void setState(VoiceState newState, String statusText) {
        state = newState;
        tvStatus.setText(statusText);
    }

    private void logLine(String s) {
        Log.d(TAG, s);
        String old = tvLog.getText() != null ? tvLog.getText().toString() : "";
        tvLog.setText(old + "\n" + s);
    }

    private String extractFirstJsonObject(String text) {
        if (text == null) return null;
        int start = text.indexOf('{');
        if (start < 0) return null;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '{') depth++;
            else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }


    private void startWavePulseAnimation(View v) {
        v.setScaleX(0.9f);
        v.setScaleY(0.9f);
        v.setAlpha(0.65f);

        v.animate()
                .scaleX(1.15f).scaleY(1.15f).alpha(1.0f)
                .setDuration(650)
                .withEndAction(() -> v.animate()
                        .scaleX(0.9f).scaleY(0.9f).alpha(0.65f)
                        .setDuration(650)
                        .withEndAction(() -> {
                            if (!isFinishing()) startWavePulseAnimation(v);
                        })
                        .start())
                .start();
    }
}
