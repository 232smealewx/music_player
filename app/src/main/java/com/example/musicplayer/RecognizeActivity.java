package com.example.musicplayer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.acrcloud.rec.ACRCloudClient;
import com.acrcloud.rec.ACRCloudConfig;
import com.acrcloud.rec.ACRCloudResult;
import com.acrcloud.rec.IACRCloudListener;
import com.acrcloud.rec.utils.ACRCloudLogger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class RecognizeActivity extends AppCompatActivity implements IACRCloudListener {

    private static final int PERMISSION_CODE = 1;

    private TextView tvResult;
    private Button btnStart;

    private ACRCloudClient mClient;
    private ACRCloudConfig mConfig;
    private boolean mProcessing = false;
    private boolean initState = false;
    private long startTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recognize);

        tvResult = findViewById(R.id.tv_result);
        btnStart = findViewById(R.id.btn_start);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Music Recognition");

        verifyPermissions();
        initACRCloud();

        btnStart.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        PERMISSION_CODE);
            } else {
                startRecognition();
            }
        });
    }

    // Initialize the ACRCloud SDK
    private void initACRCloud() {
        mConfig = new ACRCloudConfig();
        mConfig.acrcloudListener = this;
        mConfig.context = this;

        // Replace with your credentials
        mConfig.host = "identify-cn-north-1.acrcloud.cn";
        mConfig.accessKey = "cfe86b7eddfda3b8542d0ca1843c6199";
        mConfig.accessSecret = "SFMsu8jVlPKM96LRlKyynRyHTN6ptI8otA7EiECy";

        // Recorder settings
        mConfig.recorderConfig.rate = 44100; // default 44100
        mConfig.recorderConfig.channels = 1; // mono
        mConfig.recorderConfig.isVolumeCallback = true;
        mConfig.recorderConfig.reservedRecordBufferMS = 10000;

        ACRCloudLogger.setLog(true); // Enable SDK logging

        mClient = new ACRCloudClient();
        initState = mClient.initWithConfig(mConfig);
        Log.d("ACRCloud", "Init state: " + initState);
    }

    // Start the recognition process
    private void startRecognition() {
        if (!initState) {
            Toast.makeText(this, "SDK initialization failed.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!mProcessing && mClient != null) {
            mProcessing = true;
            tvResult.setText("Recognizing...");

            boolean started = mClient.startRecognize();
            if (!started) {
                mProcessing = false;
                tvResult.setText("Failed to start recognition.");
                Log.e("ACRCloud", "startRecognize() returned false");
            }

            startTime = System.currentTimeMillis();
        }
    }

    // Cancel current recognition session
    private void cancelRecognition() {
        if (mProcessing && mClient != null) {
            mClient.cancel();
        }
        reset();
    }

    // Reset recognition status
    private void reset() {
        mProcessing = false;
        tvResult.setText("");
    }

    // Recognition result callback
    @Override
    public void onResult(ACRCloudResult result) {
        reset();

        String rawResult = result.getResult();
        Log.d("ACRCloud", "Result JSON: " + rawResult);
        StringBuilder displayResult = new StringBuilder();

        try {
            JSONObject json = new JSONObject(rawResult);
            JSONObject status = json.getJSONObject("status");
            int code = status.getInt("code");

            if (code == 0 && json.has("metadata")) {
                JSONObject metadata = json.getJSONObject("metadata");

                JSONArray musics = null;
                if (metadata.has("music")) {
                    musics = metadata.getJSONArray("music");
                } else if (metadata.has("humming")) {
                    musics = metadata.getJSONArray("humming");
                }

                if (musics != null && musics.length() > 0) {
                    // 提取最佳匹配（score最高）
                    JSONObject bestMatch = musics.getJSONObject(0);
                    double bestScore = bestMatch.optDouble("score", 0.0);

                    for (int i = 1; i < musics.length(); i++) {
                        JSONObject track = musics.getJSONObject(i);
                        double score = track.optDouble("score", 0.0);
                        if (score > bestScore) {
                            bestMatch = track;
                            bestScore = score;
                        }
                    }

                    // 添加最佳匹配信息
                    String bestTitle = bestMatch.optString("title", "Unknown");
                    String bestArtist = "Unknown";
                    if (bestMatch.has("artists")) {
                        JSONArray artists = bestMatch.getJSONArray("artists");
                        if (artists.length() > 0) {
                            bestArtist = artists.getJSONObject(0).optString("name", "Unknown");
                        }
                    }

                    displayResult.append("Best Match:\n")
                            .append("Title: ").append(bestTitle)
                            .append(" | Artist: ").append(bestArtist)
                            .append(" | Score: ").append(String.format("%.2f", bestScore))
                            .append("\n\n");

                    // 显示所有匹配候选
                    for (int i = 0; i < musics.length(); i++) {
                        JSONObject track = musics.getJSONObject(i);
                        String title = track.optString("title", "Unknown");
                        String artist = "Unknown";
                        if (track.has("artists")) {
                            JSONArray artists = track.getJSONArray("artists");
                            if (artists.length() > 0) {
                                artist = artists.getJSONObject(0).optString("name", "Unknown");
                            }
                        }
                        double score = track.optDouble("score", 0.0);

                        displayResult.append(i + 1).append(". Title: ")
                                .append(title).append(" | Artist: ")
                                .append(artist).append(" | Score: ")
                                .append(String.format("%.2f", score)).append("\n");
                    }
                }
            } else {
                displayResult.append("No match found.\n");
            }

        } catch (JSONException e) {
            displayResult.append("Result parsing failed.\n");
            Log.e("ACRCloud", "JSONException: ", e);
        }

        String finalResult = displayResult.toString();
        runOnUiThread(() -> tvResult.setText(finalResult));
    }

    // Volume feedback
    @Override
    public void onVolumeChanged(double volume) {
        long time = (System.currentTimeMillis() - startTime) / 1000;
        Log.d("Volume", "Volume: " + volume + ", Time: " + time + "s");
        if (volume < 0.1) {
            Log.w("Volume", "Volume too low. Skipping.");
            return;
        }
    }

    // Release SDK on exit
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mClient != null) {
            mClient.release();
            initState = false;
            mClient = null;
        }
    }

    // Permission handling
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecognition();
            } else {
                tvResult.setText("Permission denied. Cannot access microphone.");
            }
        }
    }

    // Handle top bar back button
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            cancelRecognition();
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Request required permissions
    private void verifyPermissions() {
        String[] permissions = {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE
        };
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, permissions, PERMISSION_CODE);
                break;
            }
        }
    }
}
