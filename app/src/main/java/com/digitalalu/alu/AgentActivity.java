package com.digitalalu.alu;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.digitalalu.alu.agent.AgentEngine;
import com.digitalalu.alu.agent.QwenModelManager;
import com.digitalalu.alu.model.ChatMessage;
import com.digitalalu.alu.ui.InsetsHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI Agent Chat screen with on-device AI:
 * 1. Fixed modern responsive chat bubble UI (no squishing/word breaks).
 * 2. Horizontally scrollable quick chips.
 * 3. Qwen 2.5 0.5B (~350MB INT4, under 500MB) model manager + downloader.
 * 4. Built-in lightweight ONNX Neural Classifier fallback (~268 KB).
 */
public class AgentActivity extends AppCompatActivity {

    private LinearLayout chatBox;
    private ScrollView scrollView;
    private EditText etInput;
    private ImageButton btnSend;
    private TextView tvStatus;
    private AgentEngine engine;
    private final List<ChatMessage> messages = new ArrayList<>();
    private int maxBubbleWidth;

    private final ActivityResultLauncher<String[]> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    importLocalModel(uri);
                }
            });

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        engine = new AgentEngine(this);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        maxBubbleWidth = (int) (screenWidth * 0.82f);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF1F5F9); // Slate-100 clean background

        // ================= TOP BAR =================
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setBackgroundColor(0xFF1E40AF); // Deep Indigo/Blue
        top.setPadding(dp(12), dp(12), dp(12), dp(12));
        top.setGravity(Gravity.CENTER_VERTICAL);

        // Back button
        ImageButton btnBack = new ImageButton(this);
        btnBack.setImageResource(android.R.drawable.ic_menu_revert);
        btnBack.setBackgroundColor(Color.TRANSPARENT);
        btnBack.setColorFilter(Color.WHITE);
        btnBack.setPadding(dp(4), dp(4), dp(8), dp(4));
        btnBack.setOnClickListener(v -> finish());
        top.addView(btnBack);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView title = new TextView(this);
        title.setText("\uD83E\uDD16 ALU ASSISTANT");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        titleCol.addView(title);

        tvStatus = new TextView(this);
        updateStatusText();
        tvStatus.setTextColor(0xFFBFDBFE);
        tvStatus.setTextSize(11);
        titleCol.addView(tvStatus);
        top.addView(titleCol);

        // Model Config Button
        Button btnModel = new Button(this);
        btnModel.setText("\u2699\uFE0F Model");
        btnModel.setTextSize(11);
        btnModel.setTextColor(0xFF1E40AF);
        btnModel.setAllCaps(false);
        btnModel.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable mBg = new GradientDrawable();
        mBg.setColor(Color.WHITE);
        mBg.setCornerRadius(dp(14));
        btnModel.setBackground(mBg);
        btnModel.setOnClickListener(v -> showModelSettingsDialog());
        top.addView(btnModel);

        root.addView(top);

        // ================= CHAT SCROLL AREA =================
        scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        scrollView.setFillViewport(true);

        chatBox = new LinearLayout(this);
        chatBox.setOrientation(LinearLayout.VERTICAL);
        chatBox.setPadding(dp(12), dp(12), dp(12), dp(12));

        scrollView.addView(chatBox);
        root.addView(scrollView);

        // ================= INPUT BAR =================
        LinearLayout inputCard = new LinearLayout(this);
        inputCard.setOrientation(LinearLayout.HORIZONTAL);
        inputCard.setBackgroundColor(Color.WHITE);
        inputCard.setPadding(dp(8), dp(6), dp(8), dp(6));
        inputCard.setGravity(Gravity.CENTER_VERTICAL);
        inputCard.setElevation(dp(4));

        etInput = new EditText(this);
        etInput.setHint("Poochhiye: Shutter size, pipe cutting, rates...");
        etInput.setHintTextColor(0xFF94A3B8);
        etInput.setTextSize(14);
        etInput.setTextColor(0xFF0F172A);
        etInput.setPadding(dp(14), dp(10), dp(14), dp(10));
        etInput.setMaxLines(4);

        GradientDrawable inBg = new GradientDrawable();
        inBg.setColor(0xFFF8FAFC);
        inBg.setCornerRadius(dp(22));
        inBg.setStroke(dp(1), 0xFFE2E8F0);
        etInput.setBackground(inBg);

        LinearLayout.LayoutParams etP = new LinearLayout.LayoutParams(0, -2, 1f);
        etP.setMarginEnd(dp(8));
        etInput.setLayoutParams(etP);
        inputCard.addView(etInput);

        btnSend = new ImageButton(this);
        btnSend.setImageResource(android.R.drawable.ic_menu_send);
        btnSend.setColorFilter(Color.WHITE);
        btnSend.setPadding(dp(10), dp(10), dp(10), dp(10));

        GradientDrawable sendBg = new GradientDrawable();
        sendBg.setShape(GradientDrawable.OVAL);
        sendBg.setColor(0xFF2563EB);
        btnSend.setBackground(sendBg);

        LinearLayout.LayoutParams btnP = new LinearLayout.LayoutParams(dp(44), dp(44));
        btnSend.setLayoutParams(btnP);
        btnSend.setOnClickListener(v -> sendMessage());
        btnSend.setEnabled(false);
        btnSend.setAlpha(0.4f);
        inputCard.addView(btnSend);

        root.addView(inputCard);

        etInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                boolean hasText = s != null && s.toString().trim().length() > 0;
                btnSend.setEnabled(hasText);
                btnSend.setAlpha(hasText ? 1f : 0.4f);
            }
        });

        setContentView(root);
        InsetsHelper.apply(root, top);

        // Show welcome greeting
        addAgentMessage(engine.getGreeting());

        // Quick action horizontal scroll chips
        addQuickChips();
    }

    private void updateStatusText() {
        if (tvStatus == null) return;
        if (engine.isQwenActive()) {
            tvStatus.setText("Online \u2022 \uD83E\uDD16 Qwen 0.5B Active");
        } else if (engine.isOnnxReady()) {
            tvStatus.setText("Online \u2022 \uD83E\uDD16 ONNX AI Active");
        } else {
            tvStatus.setText("Online \u2022 Offline Assistant");
        }
    }

    private void sendMessage() {
        String text = etInput.getText().toString().trim();
        if (text.isEmpty()) return;

        addUserMessage(text);
        etInput.setText("");

        removeQuickChips();
        addTypingIndicator();

        chatBox.postDelayed(() -> {
            removeTypingIndicator();
            String response = engine.respond(text);
            addAgentMessage(response);
        }, 400 + (long)(Math.random() * 400));
    }

    private void addUserMessage(String text) {
        messages.add(new ChatMessage(ChatMessage.ROLE_USER, text));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        row.setGravity(Gravity.END | Gravity.TOP);
        row.setPadding(dp(32), dp(4), dp(4), dp(4));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.END);
        inner.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));

        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(14);
        bubble.setTextColor(Color.WHITE);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setLineSpacing(dp(2), 1.15f);
        bubble.setMaxWidth(maxBubbleWidth);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF2563EB); // Vibrant royal blue
        bg.setCornerRadii(new float[]{dp(16), dp(16), dp(4), dp(4), dp(16), dp(16), dp(16), dp(16)});
        bubble.setBackground(bg);
        bubble.setElevation(dp(1));

        inner.addView(bubble);
        row.addView(inner);

        // User Avatar
        TextView avatar = new TextView(this);
        avatar.setText("\uD83D\uDC64");
        avatar.setTextSize(14);
        avatar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams avP = new LinearLayout.LayoutParams(dp(32), dp(32));
        avP.setMarginStart(dp(8));
        avP.topMargin = dp(2);
        avatar.setLayoutParams(avP);

        GradientDrawable avBg = new GradientDrawable();
        avBg.setShape(GradientDrawable.OVAL);
        avBg.setColor(0xFFDBEAFE);
        avatar.setBackground(avBg);
        row.addView(avatar);

        chatBox.addView(row);
        scrollToBottom();
    }

    private void addAgentMessage(String text) {
        messages.add(new ChatMessage(ChatMessage.ROLE_AGENT, text));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        row.setGravity(Gravity.START | Gravity.TOP);
        row.setPadding(dp(4), dp(4), dp(28), dp(4));

        // Assistant Avatar
        TextView avatar = new TextView(this);
        avatar.setText("\uD83E\uDD16");
        avatar.setTextSize(15);
        avatar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams avP = new LinearLayout.LayoutParams(dp(34), dp(34));
        avP.setMarginEnd(dp(8));
        avP.topMargin = dp(2);
        avatar.setLayoutParams(avP);

        GradientDrawable avBg = new GradientDrawable();
        avBg.setShape(GradientDrawable.OVAL);
        avBg.setColor(0xFFE2E8F0);
        avatar.setBackground(avBg);
        row.addView(avatar);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));

        // Header with label + badge
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hP = new LinearLayout.LayoutParams(-2, -2);
        hP.bottomMargin = dp(3);
        header.setLayoutParams(hP);

        TextView label = new TextView(this);
        label.setText("ALU Assistant");
        label.setTextSize(11);
        label.setTextColor(0xFF475569);
        label.setTypeface(null, Typeface.BOLD);
        header.addView(label);

        TextView badge = new TextView(this);
        badge.setText(engine.isQwenActive() ? "Qwen 0.5B" : (engine.isOnnxReady() ? "ONNX AI" : "Offline"));
        badge.setTextSize(9);
        badge.setTextColor(0xFF2563EB);
        badge.setPadding(dp(6), dp(1), dp(6), dp(1));
        GradientDrawable bBg = new GradientDrawable();
        bBg.setColor(0xFFEFF6FF);
        bBg.setCornerRadius(dp(8));
        bBg.setStroke(dp(1), 0xFFBFDBFE);
        badge.setBackground(bBg);
        LinearLayout.LayoutParams bP = new LinearLayout.LayoutParams(-2, -2);
        bP.setMarginStart(dp(6));
        badge.setLayoutParams(bP);
        header.addView(badge);

        inner.addView(header);

        // Bubble container with proper max width & padding
        TextView bubble = new TextView(this);
        bubble.setText(formatMarkdown(text));
        bubble.setTextSize(14);
        bubble.setTextColor(0xFF0F172A);
        bubble.setPadding(dp(14), dp(12), dp(14), dp(12));
        bubble.setLineSpacing(dp(3), 1.15f);
        bubble.setMaxWidth(maxBubbleWidth);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadii(new float[]{dp(4), dp(4), dp(16), dp(16), dp(16), dp(16), dp(16), dp(16)});
        bg.setStroke(dp(1), 0xFFE2E8F0);
        bubble.setBackground(bg);
        bubble.setElevation(dp(1));

        inner.addView(bubble);
        row.addView(inner);

        chatBox.addView(row);
        scrollToBottom();
    }

    private void addTypingIndicator() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(4), dp(40), dp(4));
        row.setTag("typing");

        TextView avatar = new TextView(this);
        avatar.setText("\uD83E\uDD16");
        avatar.setTextSize(14);
        avatar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams avP = new LinearLayout.LayoutParams(dp(32), dp(32));
        avP.setMarginEnd(dp(8));
        avatar.setLayoutParams(avP);

        GradientDrawable avBg = new GradientDrawable();
        avBg.setShape(GradientDrawable.OVAL);
        avBg.setColor(0xFFE2E8F0);
        avatar.setBackground(avBg);
        row.addView(avatar);

        TextView dots = new TextView(this);
        dots.setText("\u2022\u2022\u2022 Typing...");
        dots.setTextSize(13);
        dots.setTextColor(0xFF64748B);
        dots.setPadding(dp(12), dp(8), dp(12), dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), 0xFFE2E8F0);
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
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        chipScroll.setTag("chips");

        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(dp(4), dp(8), dp(4), dp(8));

        String[][] chips = {
            {"\uD83D\uDCD0 Calculation", "calculation kaise kare"},
            {"\uD83E\uDE9A Sutter Size", "sutter calculation formula"},
            {"\uD83D\uDD29 Muliya", "muliya calculation"},
            {"\uD83E\uDE8F Pipe Cutting", "pipe cutting plan"},
            {"\uD83D\uDCB0 Price / Rates", "price setup"},
            {"\uD83D\uDC65 Customer", "customer records"},
            {"\uD83D\uDCCB Sheet PCO", "manual sheet cutting"},
            {"\u2753 Help", "help"}
        };

        for (String[] chip : chips) {
            Button b = new Button(this);
            b.setText(chip[0]);
            b.setTextSize(12);
            b.setTextColor(0xFF1D4ED8);
            b.setAllCaps(false);
            b.setPadding(dp(14), dp(8), dp(14), dp(8));

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFFEFF6FF);
            bg.setCornerRadius(dp(18));
            bg.setStroke(dp(1), 0xFFBFDBFE);
            b.setBackground(bg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(dp(8));
            b.setLayoutParams(lp);

            final String query = chip[1];
            b.setOnClickListener(v -> {
                etInput.setText(query);
                sendMessage();
            });
            chipRow.addView(b);
        }

        chipScroll.addView(chipRow);
        chatBox.addView(chipScroll);
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

    /**
     * Converts markdown **bold** to actual Android Typeface bold spans.
     * Crucially avoids inserting zero-width spaces that caused awkward line breaks!
     */
    private CharSequence formatMarkdown(String text) {
        if (text == null) return "";
        Pattern pattern = Pattern.compile("\\*\\*(.+?)\\*\\*");
        Matcher matcher = pattern.matcher(text);

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            ssb.append(text.substring(lastEnd, matcher.start()));
            int boldStart = ssb.length();
            ssb.append(matcher.group(1));
            int boldEnd = ssb.length();
            ssb.setSpan(new StyleSpan(Typeface.BOLD), boldStart, boldEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            lastEnd = matcher.end();
        }
        ssb.append(text.substring(lastEnd));
        return ssb;
    }

    // ================= MODEL SETTINGS DIALOG =================
    private void showModelSettingsDialog() {
        final QwenModelManager qmm = engine.getQwenModelManager();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("\uD83E\uDD16 AI Model Configuration");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(16), dp(20), dp(16));

        // Qwen Section
        TextView qwenTitle = new TextView(this);
        qwenTitle.setText("1. Qwen 2.5 0.5B Instruct (Under 500MB)");
        qwenTitle.setTextSize(14);
        qwenTitle.setTypeface(null, Typeface.BOLD);
        qwenTitle.setTextColor(0xFF1E293B);
        layout.addView(qwenTitle);

        TextView qwenDesc = new TextView(this);
        qwenDesc.setText("Compact ~350MB INT4 local generative model. High intelligence for aluminium fabrication dialogues.");
        qwenDesc.setTextSize(12);
        qwenDesc.setTextColor(0xFF64748B);
        layout.addView(qwenDesc);

        final TextView tvQwenStatus = new TextView(this);
        boolean isQwenInstalled = qmm != null && qmm.isModelDownloaded();
        tvQwenStatus.setText(isQwenInstalled ?
                "Status: \u2705 Installed (" + qmm.getFormattedModelSize() + ") \u2022 Active" :
                "Status: \u274C Not installed (Tap download below)");
        tvQwenStatus.setTextSize(12);
        tvQwenStatus.setTextColor(isQwenInstalled ? 0xFF16A34A : 0xFFDC2626);
        tvQwenStatus.setTypeface(null, Typeface.BOLD);
        layout.addView(tvQwenStatus);

        final ProgressBar pBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pBar.setMax(100);
        pBar.setVisibility(View.GONE);
        layout.addView(pBar);

        final TextView tvProgress = new TextView(this);
        tvProgress.setTextSize(11);
        tvProgress.setTextColor(0xFF2563EB);
        tvProgress.setVisibility(View.GONE);
        layout.addView(tvProgress);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(8), 0, dp(16));

        Button btnDownload = new Button(this);
        btnDownload.setText("\u2B07\uFE0F Download (350MB)");
        btnDownload.setTextSize(11);
        btnDownload.setAllCaps(false);
        btnRow.addView(btnDownload);

        Button btnImport = new Button(this);
        btnImport.setText("\uD83D\uDCC1 Select File");
        btnImport.setTextSize(11);
        btnImport.setAllCaps(false);
        btnRow.addView(btnImport);

        layout.addView(btnRow);

        // ONNX Lite Section
        TextView onnxTitle = new TextView(this);
        onnxTitle.setText("2. Built-in ONNX Lite Agent (268 KB)");
        onnxTitle.setTextSize(14);
        onnxTitle.setTypeface(null, Typeface.BOLD);
        onnxTitle.setTextColor(0xFF1E293B);
        layout.addView(onnxTitle);

        TextView onnxDesc = new TextView(this);
        onnxDesc.setText("Status: \u2705 Ready \u2022 Instant on-device classification (<1ms) \u2022 Zero internet needed.");
        onnxDesc.setTextSize(12);
        onnxDesc.setTextColor(0xFF16A34A);
        layout.addView(onnxDesc);

        builder.setView(layout);
        builder.setPositiveButton("OK", null);

        if (isQwenInstalled) {
            builder.setNegativeButton("Delete Qwen", (d, w) -> {
                if (qmm != null) {
                    qmm.deleteModel();
                    Toast.makeText(this, "Qwen model deleted", Toast.LENGTH_SHORT).show();
                    updateStatusText();
                }
            });
        }

        AlertDialog dialog = builder.create();

        btnDownload.setOnClickListener(v -> {
            if (qmm == null) return;
            pBar.setVisibility(View.VISIBLE);
            tvProgress.setVisibility(View.VISIBLE);
            btnDownload.setEnabled(false);

            qmm.startDownload(new QwenModelManager.DownloadListener() {
                @Override
                public void onProgress(int percent, long bytesDownloaded, long totalBytes) {
                    pBar.setProgress(percent);
                    tvProgress.setText(String.format("Downloading: %d%% (%.1f / %.1f MB)",
                            percent, bytesDownloaded / (1024.0 * 1024.0), totalBytes / (1024.0 * 1024.0)));
                }

                @Override
                public void onSuccess(File modelFile) {
                    pBar.setVisibility(View.GONE);
                    tvProgress.setText("\u2705 Download complete! Initializing Qwen...");
                    if (engine.getQwenEngine() != null) {
                        engine.getQwenEngine().tryInit();
                    }
                    updateStatusText();
                    Toast.makeText(AgentActivity.this, "Qwen 0.5B model ready!", Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                }

                @Override
                public void onError(String message) {
                    pBar.setVisibility(View.GONE);
                    tvProgress.setText("\u274C " + message);
                    btnDownload.setEnabled(true);
                }
            });
        });

        btnImport.setOnClickListener(v -> {
            dialog.dismiss();
            filePickerLauncher.launch(new String[]{"*/*"});
        });

        dialog.show();
    }

    private void importLocalModel(Uri uri) {
        final QwenModelManager qmm = engine.getQwenModelManager();
        if (qmm == null) return;

        Toast.makeText(this, "Importing model file...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean success = qmm.importModel(uri);
            runOnUiThread(() -> {
                if (success) {
                    if (engine.getQwenEngine() != null) {
                        engine.getQwenEngine().tryInit();
                    }
                    updateStatusText();
                    Toast.makeText(this, "\u2705 Model imported successfully!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "\u274C Failed to import model", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void scrollToBottom() {
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (engine != null) {
            engine.close();
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
