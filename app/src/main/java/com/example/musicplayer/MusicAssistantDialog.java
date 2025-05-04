package com.example.musicplayer.ai;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.musicplayer.MusicService;
import com.example.musicplayer.R;

import java.util.Arrays;
import java.util.List;

public class MusicAssistantDialog extends Dialog {
    private MusicAssistant musicAssistant;
    private TextView chatHistoryTextView;
    private EditText userInputEditText;
    private Button sendButton;
    private ImageButton closeButton;
    private ScrollView scrollView;
    private LinearLayout suggestedQuestionsLayout;
    private HorizontalScrollView horizontalScrollView;

    // 预设的推荐问题
    private final List<String> suggestedQuestions = Arrays.asList(
            "今天推荐我听什么歌？",
            "今天是下雨天，有什么歌曲推荐？",
            "有什么适合工作时听的歌？",
            "推荐一首舒缓的歌曲",
            "推荐一首适合运动的歌曲",
            "最近有什么流行的新歌？"
    );

    public MusicAssistantDialog(@NonNull Context context, MusicService musicService, String apiKey) {
        super(context);
        musicAssistant = new MusicAssistant(context, musicService, apiKey);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_music_assistant);

        // 设置对话框大小
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        getWindow().setAttributes(params);

        // 初始化视图
        chatHistoryTextView = findViewById(R.id.chatHistoryTextView);
        userInputEditText = findViewById(R.id.userInputEditText);
        sendButton = findViewById(R.id.sendButton);
        closeButton = findViewById(R.id.closeButton);
        scrollView = findViewById(R.id.scrollView);
        suggestedQuestionsLayout = findViewById(R.id.suggestedQuestionsLayout);
        horizontalScrollView = findViewById(R.id.horizontalScrollView);

        // 添加推荐问题按钮
        setupSuggestedQuestions();

        // 设置发送按钮点击事件
        sendButton.setOnClickListener(v -> {
            String userMessage = userInputEditText.getText().toString().trim();
            if (!userMessage.isEmpty()) {
                sendMessage(userMessage);
            }
        });

        // 设置关闭按钮点击事件
        closeButton.setOnClickListener(v -> dismiss());

        // 添加欢迎消息
        appendToChatHistory("音乐管家: 你好！我是你的音乐管家。我可以帮你了解音乐知识、推荐音乐，或者帮你找到符合心情的歌曲。有什么我可以帮你的吗？");
    }

    private void setupSuggestedQuestions() {
        suggestedQuestionsLayout.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(getContext());

        for (String question : suggestedQuestions) {
            Button questionButton = (Button) inflater.inflate(R.layout.suggested_question_button, suggestedQuestionsLayout, false);
            questionButton.setText(question);
            questionButton.setOnClickListener(v -> sendMessage(question));
            suggestedQuestionsLayout.addView(questionButton);

            // 添加间距
            View space = new View(getContext());
            LinearLayout.LayoutParams spaceParams = new LinearLayout.LayoutParams(
                    (int) getContext().getResources().getDisplayMetrics().density * 8,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            space.setLayoutParams(spaceParams);
            suggestedQuestionsLayout.addView(space);
        }
    }

    private void sendMessage(String userMessage) {
        appendToChatHistory("你: " + userMessage);
        userInputEditText.setText("");

        // 显示加载中提示
        appendToChatHistory("音乐管家: 思考中...");

        // 发送请求
        musicAssistant.chat(userMessage, new MusicAssistant.ChatCallback() {
            @Override
            public void onResponse(String response) {
                // 移除"思考中..."并添加实际回复
                removePreviousLine();
                appendToChatHistory("音乐管家: " + response);
            }

            @Override
            public void onError(String errorMessage) {
                // 移除"思考中..."并添加错误信息
                removePreviousLine();
                appendToChatHistory("音乐管家: 抱歉，我遇到了问题: " + errorMessage);
            }
        });
    }

    private void appendToChatHistory(String message) {
        chatHistoryTextView.append(message + "\n\n");
        // 滚动到底部
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void removePreviousLine() {
        String currentText = chatHistoryTextView.getText().toString();
        int lastIndex = currentText.lastIndexOf("音乐管家: 思考中...");
        if (lastIndex != -1) {
            String newText = currentText.substring(0, lastIndex);
            chatHistoryTextView.setText(newText);
        }
    }
}