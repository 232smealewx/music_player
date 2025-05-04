package com.example.musicplayer.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.musicplayer.MusicService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MusicAssistant {
    private static final String TAG = "MusicAssistant";
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String SYSTEM_PROMPT = "你是一个专业的音乐助手，你的工作是帮助用户了解音乐知识、推荐音乐、提供音乐背景信息等。如果用户要求推荐音乐，请根据他们的喜好和情绪推荐具体的歌曲。回答中请明确标出歌曲名，格式为【歌曲名】，以便系统识别并播放。";

    private final String apiKey;
    private final OkHttpClient client;
    private final Context context;
    private final MusicService musicService;
    private final Handler mainHandler;
    private final List<ChatMessage> chatHistory;

    public interface ChatCallback {
        void onResponse(String response);
        void onError(String errorMessage);
    }

    public MusicAssistant(Context context, MusicService musicService, String apiKey) {
        this.context = context;
        this.musicService = musicService;
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.chatHistory = new ArrayList<>();
    }

    public void chat(String userMessage, ChatCallback callback) {
        // 添加用户消息到历史
        chatHistory.add(new ChatMessage("user", userMessage));

        // 构建请求
        try {
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("model", "gpt-4o");
            jsonBody.put("temperature", 0.7);

            JSONArray messagesArray = new JSONArray();

            // 添加系统提示
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", SYSTEM_PROMPT + "\n可用的音乐列表: " + getAvailableSongsAsString());
            messagesArray.put(systemMessage);

            // 添加历史消息
            for (ChatMessage message : chatHistory) {
                JSONObject messageObj = new JSONObject();
                messageObj.put("role", message.getRole());
                messageObj.put("content", message.getContent());
                messagesArray.put(messageObj);
            }

            jsonBody.put("messages", messagesArray);

            RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> callback.onError("网络错误: " + e.getMessage()));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) {
                            mainHandler.post(() -> callback.onError("API错误: " + response.code()));
                            return;
                        }

                        String responseData = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseData);
                        String assistantResponse = jsonResponse.getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");

                        // 添加助手回复到历史
                        chatHistory.add(new ChatMessage("assistant", assistantResponse));

                        // 检查是否包含音乐推荐
                        List<String> recommendedSongs = extractRecommendedSongs(assistantResponse);
                        if (!recommendedSongs.isEmpty()) {
                            // 尝试播放推荐的第一首歌
                            String firstSong = recommendedSongs.get(0);
                            tryPlayRecommendedSong(firstSong);
                        }

                        mainHandler.post(() -> callback.onResponse(assistantResponse));
                    } catch (JSONException e) {
                        mainHandler.post(() -> callback.onError("解析错误: " + e.getMessage()));
                    }
                }
            });
        } catch (JSONException e) {
            callback.onError("请求构建错误: " + e.getMessage());
        }
    }

    // 从回复中提取推荐的歌曲
    private List<String> extractRecommendedSongs(String response) {
        List<String> songs = new ArrayList<>();
        // 使用正则表达式查找【歌曲名】格式的推荐
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("【(.*?)】");
        java.util.regex.Matcher matcher = pattern.matcher(response);

        while (matcher.find()) {
            String songName = matcher.group(1);
            if (songName != null && !songName.isEmpty()) {
                // 检查推荐的歌曲是否在可用列表中
                String fullSongName = findMatchingSong(songName);
                if (fullSongName != null) {
                    songs.add(fullSongName);
                }
            }
        }
        return songs;
    }

    // 查找匹配的歌曲文件名
    private String findMatchingSong(String partialName) {
        try {
            String[] availableSongs = context.getAssets().list("music");
            for (String song : availableSongs) {
                // 移除扩展名进行比较
                String songNameWithoutExt = song.substring(0, song.lastIndexOf("."));
                if (songNameWithoutExt.toLowerCase().contains(partialName.toLowerCase())) {
                    return song;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to list music files", e);
        }
        return null;
    }

    // 尝试播放推荐的歌曲
    private void tryPlayRecommendedSong(String songName) {
        if (musicService != null) {
            try {
                mainHandler.post(() -> {
                    try {
                        musicService.playMusic(songName);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to play recommended song", e);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to post play command", e);
            }
        }
    }

    // 获取可用的音乐列表
    public List<String> getAvailableSongs() {
        List<String> songList = new ArrayList<>();
        try {
            String[] songs = context.getAssets().list("music");
            for (String song : songs) {
                songList.add(song);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to list music files", e);
        }
        return songList;
    }

    // 获取可用的音乐列表（字符串形式）
    private String getAvailableSongsAsString() {
        List<String> songs = getAvailableSongs();
        StringBuilder sb = new StringBuilder();
        for (String song : songs) {
            // 移除扩展名
            String songNameWithoutExt = song.substring(0, song.lastIndexOf("."));
            sb.append(songNameWithoutExt).append(", ");
        }
        // 移除最后的逗号和空格
        if (sb.length() > 2) {
            sb.setLength(sb.length() - 2);
        }
        return sb.toString();
    }

    // 清除聊天历史
    public void clearChatHistory() {
        chatHistory.clear();
    }
}