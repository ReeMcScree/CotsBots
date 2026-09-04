package com.example.wheel;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
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

public class MicroPhoneActivity extends AppCompatActivity {

    private Button btnStart;
    private TextView tvResult;

    private ActivityResultLauncher<Intent> speechLauncher;
    private String recognizedText = "";

    private static final String OPENAI_API_KEY =
            "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";

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_microphone);

        btnStart = findViewById(R.id.btnStart);
        tvResult = findViewById(R.id.tvResult);

        speechLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {

                            ArrayList<String> list =
                                    result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

                            if (list != null && list.size() > 0) {

                                recognizedText = list.get(0).toLowerCase(Locale.ROOT);
                                tvResult.setText("Speech: " + recognizedText);

                                if (recognizedText.contains("hey eric")) {
                                    extractCommandFromText(recognizedText);
                                } else {
                                    tvResult.setText("Say: 'Hey Eric ...' to activate command mode.");
                                }
                            }
                        }
                    }
                }
        );

        btnStart.setOnClickListener(v -> startSpeechToText());
    }

    private void startSpeechToText() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...");
        speechLauncher.launch(intent);
    }

    public String getRecognizedText() {
        return recognizedText;
    }

    private void extractCommandFromText(String text) {

        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();

                MediaType JSON = MediaType.get("application/json; charset=utf-8");

                JSONObject jsonBody = new JSONObject();
                jsonBody.put("model", "gpt-4o-mini");
                jsonBody.put("temperature", 0.0);

                JSONObject sys = new JSONObject();
                sys.put("role", "system");
                sys.put("content",
                        "You are a robot voice command parser. Based on the user's utterance, return a JSON command. " +
                                "If the text does NOT contain 'hey eric' (case-insensitive), you MUST return {\"type\":\"IGNORE\"}. " +

                                "== MOVE COMMAND ==" +
                                "If the user says forward/backward/left/right or w/a/s/d, return: " +
                                "{\"type\":\"MOVE\",\"command\":\"W|A|S|D\",\"duration\":<milliseconds>}. " +
                                "Supported time formats: e.g., '2 seconds', '500 ms'. " +
                                "Rules: 'ms' = milliseconds; 's' = seconds × 1000. " +

                                "== TRACK COMMAND (COLOR FOLLOW) ==" +
                                "If the user says any of: track, follow, tail, trace, return: " +
                                "{\"type\":\"TRACK\",\"color\":\"GREEN|RED|BLUE|YELLOW|BLACK|WHITE|ORANGE|PURPLE\"}. " +
                                "Extract the color from the user's utterance. " +

                                "The output MUST be valid JSON with no extra text. " +
                                "If you cannot understand the instruction, return {\"type\":\"IGNORE\"}."
                );


                JSONObject user = new JSONObject();
                user.put("role", "user");
                user.put("content", text);

                jsonBody.put("messages", new org.json.JSONArray().put(sys).put(user));

                RequestBody body = RequestBody.create(jsonBody.toString(), JSON);

                Request request = new Request.Builder()
                        .url("https://api.openai.com/v1/chat/completions")
                        .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                        .post(body)
                        .build();

                Response response = client.newCall(request).execute();
                String responseStr = response.body().string();

                Log.d("CHATGPT", responseStr);

                JSONObject root = new JSONObject(responseStr);
                String content = root
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");

                runOnUiThread(() -> tvResult.setText(
                        "Speech: " + recognizedText + "\n\nParsed:\n" + content
                ));

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
