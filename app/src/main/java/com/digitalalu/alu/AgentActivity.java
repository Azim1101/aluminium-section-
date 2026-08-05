package com.digitalalu.alu;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.digitalalu.alu.agent.AgentEngine;
import com.digitalalu.alu.model.ChatMessage;
import com.digitalalu.alu.ui.InsetsHelper;

import java.util.ArrayList;
import java.util.List;

/** AI Agent chat screen — local pattern-matching chatbot */
public class AgentActivity extends AppCompatActivity {

    private LinearLayout chatBox;
    private ScrollView scrollView;
    private EditText etInput;
    private ImageButton btnSend;
    private AgentEngine engine;
    private List<ChatMessage> messages = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        engine = new AgentEngine(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF4F6FB);

        // ----- Top Bar -----
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setBackgroundColor(0xFF2563EB);
        top.setPadding(dp(16), dp(12), dp(16), dp(12));
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("\uD83E\uDD16 ALU ASSISTANT");
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        title.setTypeface(null, Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(title);

        TextView status = new TextView(this);
        status.setText("Online \u2022 Offline AI");
        status.setTextColor(0xFFCFE0FF);
        status.setTextSize(10);
        top.addView(status);

        root.addView(top);

        // ----- Chat Area -----
        scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        scrollView.setFillViewport(true);

        chatBox = new LinearLayout(this);
        chatBox.setOrientation(LinearLayout.VERTICAL);
        chatBox.setPadding(dp(12), dp(12), dp(12), dp(12));

        scrollView.addView(chatBox);
        root.addView(scrollView);

        // ----- Input Bar -----
        LinearLayout inputBar = new LinearLayout(this);
        inputBar.setOrientation(LinearLayout.HORIZONTAL);
        inputBar.setBackgroundColor(Color.WHITE);
        inputBar.setPadding(dp(8), dp(8), dp(8), dp(8));
        inputBar.setGravity(Gravity.CENTER_VERTICAL);
        inputBar.setElevation(dp(4));

        etInput = new EditText(this);
        etInput.setHint("Type your question...");
        etInput.setTextSize(15);
        etInput.setBackground(getDrawable(R.drawable.bg_input));
        etInput.setPadding(dp(14), dp(10), dp(14), dp(10));
        etInput.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        etInput.setMaxLines(4);
        inputBar.addView(etInput);

        btnSend = new ImageButton(this);
        btnSend.setImageResource(android.R.drawable.ic_menu_send);
        btnSend.setBackgroundColor(0xFF2563EB);
        btnSend.setPadding(dp(12), dp(12), dp(12), dp(12));
        btnSend.setColorFilter(Color.WHITE);
        LinearLayout.LayoutParams btnP = new LinearLayout.LayoutParams(dp(48), dp(48));
        btnP.setMarginStart(dp(8));
        btnSend.setLayoutParams(btnP);
        btnSend.setOnClickListener(v -> sendMessage());
        btnSend.setEnabled(false);
        btnSend.setAlpha(0.5f);
        inputBar.addView(btnSend);

        root.addView(inputBar);

        // Text watcher for send button
        etInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                boolean hasText = s.toString().trim().length() > 0;
                btnSend.setEnabled(hasText);
                btnSend.setAlpha(hasText ? 1f : 0.5f);
            }
        });

        setContentView(root);
        InsetsHelper.apply(root, top);

        // Show initial greeting
        addAgentMessage(engine.getGreeting());

        // Quick action chips
        addQuickChips();
    }

    private void sendMessage() {
        String text = etInput.getText().toString().trim();
        if (text.isEmpty()) return;

        addUserMessage(text);
        etInput.setText("");

        // Remove quick chips after first message
        removeQuickChips();

        // Simulate typing delay
        addTypingIndicator();

        chatBox.postDelayed(() -> {
            removeTypingIndicator();
            String response = engine.respond(text);
            addAgentMessage(response);
        }, 500 + (long)(Math.random() * 500));
    }

    private void addUserMessage(String text) {
        messages.add(new ChatMessage(ChatMessage.ROLE_USER, text));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        row.setPadding(dp(40), dp(4), dp(8), dp(4));

        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(14);
        bubble.setTextColor(Color.WHITE);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF2563EB);
        bg.setCornerRadii(new float[]{dp(16), dp(16), dp(4), dp(4), dp(16), dp(16), dp(16), dp(16)});
        bubble.setBackground(bg);

        row.addView(bubble);
        chatBox.addView(row);
        scrollToBottom();
    }

    private void addAgentMessage(String text) {
        messages.add(new ChatMessage(ChatMessage.ROLE_AGENT, text));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.START);
        row.setPadding(dp(8), dp(4), dp(40), dp(4));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);

        // Agent label
        TextView label = new TextView(this);
        label.setText("\uD83E\uDD16 ALU Assistant");
        label.setTextSize(10);
        label.setTextColor(0xFF66758C);
        label.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.bottomMargin = dp(2);
        label.setLayoutParams(lp);
        inner.addView(label);

        TextView bubble = new TextView(this);
        bubble.setText(formatMessage(text));
        bubble.setTextSize(14);
        bubble.setTextColor(0xFF152236);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setLineSpacing(dp(2), 1f);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadii(new float[]{dp(4), dp(4), dp(16), dp(16), dp(16), dp(16), dp(16), dp(16)});
        bg.setStroke(dp(1), 0xFFE5EAF3);
        bubble.setBackground(bg);

        inner.addView(bubble);
        row.addView(inner);
        chatBox.addView(row);
        scrollToBottom();
    }

    private void addTypingIndicator() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.START);
        row.setPadding(dp(8), dp(4), dp(40), dp(4));
        row.setTag("typing");

        TextView dots = new TextView(this);
        dots.setText("\u2022\u2022\u2022");
        dots.setTextSize(20);
        dots.setTextColor(0xFF66758C);
        dots.setPadding(dp(14), dp(8), dp(14), dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadii(new float[]{dp(4), dp(4), dp(16), dp(16), dp(16), dp(16), dp(16), dp(16)});
        bg.setStroke(dp(1), 0xFFE5EAF3);
        dots.setBackground(bg);

        row.addView(dots);
        chatBox.addView(row);
        scrollToBottom();
    }

    private void removeTypingIndicator() {
        for (int i = chatBox.getChildCount() - 1; i >= 0; i--) {
            View v = chatBox.getChildAt(i);
            if ("typing".equals(v.getTag())) {
                chatBox.removeViewAt(i);
                break;
            }
        }
    }

    private void addQuickChips() {
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(dp(8), dp(8), dp(8), dp(8));
        chipRow.setTag("chips");

        String[] chips = {"Help", "Calculation", "Price", "Customer", "Pipe cutting"};
        for (String chip : chips) {
            Button b = new Button(this);
            b.setText(chip);
            b.setTextSize(11);
            b.setTextColor(0xFF2563EB);
            b.setAllCaps(false);
            b.setPadding(dp(12), dp(6), dp(12), dp(6));
            b.setBackgroundColor(0xFFE8F0FF);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFFE8F0FF);
            bg.setCornerRadius(dp(16));
            b.setBackground(bg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(dp(6));
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> {
                etInput.setText(chip.toLowerCase());
                sendMessage();
            });
            chipRow.addView(b);
        }

        chatBox.addView(chipRow);
    }

    private void removeQuickChips() {
        for (int i = chatBox.getChildCount() - 1; i >= 0; i--) {
            View v = chatBox.getChildAt(i);
            if ("chips".equals(v.getTag())) {
                chatBox.removeViewAt(i);
                break;
            }
        }
    }

    private String formatMessage(String text) {
        // Simple formatting: **bold** -> Unicode bold
        return text
            .replaceAll("\\*\\*(.+?)\\*\\*", "\u200B$1\u200B")
            .replaceAll("`(.+?)`", "$1");
    }

    private void scrollToBottom() {
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
