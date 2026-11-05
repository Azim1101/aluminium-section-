package com.digitalalu.alu;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.digitalalu.alu.ai.AiReadiness;
import com.digitalalu.alu.ai.AiSettings;
import com.digitalalu.alu.ai.HybridAgent;
import com.digitalalu.alu.ai.ModelCatalog;
import com.digitalalu.alu.ai.ModelStore;
import com.digitalalu.alu.ai.LocalLlm;
import com.digitalalu.alu.ai.LocalModelHost;
import com.digitalalu.alu.agent.AgentEngine;
import com.digitalalu.alu.agent.OnDeviceTextGenerator;
import com.digitalalu.alu.model.ChatMessage;
import com.digitalalu.alu.ui.InsetsHelper;
import com.digitalalu.alu.voice.VoiceEngine;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * WhatsApp-Themed AI Agent Chat Interface.
 * Powered by on-device text generator model with full app control.
 */
public class AgentActivity extends AppCompatActivity {

    private LinearLayout chatBox;
    private ScrollView scrollView;
    private EditText etInput;
    private ImageButton btnSend;
    private ImageButton btnAttach;
    private ImageButton btnMic;                 // v1.0 — voice input
    private VoiceEngine voice;                  // v1.0 — Hindi/Gujarati/English ASR
    private static final int REQ_MIC = 4101;
    private TextView tvStatus;
    private HorizontalScrollView chipScrollView;
    private LinearLayout chipRow;

    private AgentEngine engine;
    private HybridAgent hybrid;
    /** one request at a time — a second tap must not stack up work */
    private volatile boolean thinking = false;
    /* v2.6 single-model: download/load banner */
    private LinearLayout modelBar;
    private TextView modelText;
    private android.widget.ProgressBar modelProgress;
    private long lastDownloadTap = 0L;
    private TextView btnThink;
    private String pendingThink = "";
    // v2.9.1 live streaming bubble
    private TextView liveThink, liveAnswer;
    private final StringBuilder liveThinkBuf = new StringBuilder();
    private final StringBuilder liveRawBuf = new StringBuilder();
    private volatile boolean livePosted = false;
    private Button modelBtn;
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean modelPolling = false;
    private final List<ChatMessage> messages = new ArrayList<>();
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        engine = new AgentEngine(this);

        // ---- Modern assistant layout (v2.8.1): flat header, full-width answers,
        //      user pills on the right, floating composer. Follows app light/dark theme.
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(c(R.color.bg));

        View topBar = buildTopBar();
        root.addView(topBar);

        scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);

        chatBox = new LinearLayout(this);
        chatBox.setOrientation(LinearLayout.VERTICAL);
        chatBox.setPadding(dp(16), dp(8), dp(16), dp(12));
        scrollView.addView(chatBox);
        root.addView(scrollView);

        root.addView(buildModelBar());

        // suggestion chips (only until the first message is sent)
        chipScrollView = new HorizontalScrollView(this);
        chipScrollView.setHorizontalScrollBarEnabled(false);
        chipScrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(dp(12), dp(4), dp(12), dp(8));
        chipScrollView.addView(chipRow);
        root.addView(chipScrollView);

        root.addView(buildComposer());

        setContentView(root);
        InsetsHelper.apply(root, topBar);

        populateQuickChips();
        addWelcome();

        maybeShowSetupGuidance();
        updateBrainStatus();
        refreshModelBar();
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (hybrid != null) hybrid.reloadConfig();
            updateBrainStatus();
            refreshModelBar();
        } catch (Throwable ignored) { }
    }

    @Override
    protected void onPause() {
        super.onPause();
        modelPolling = false;
        ui.removeCallbacksAndMessages(null);
        // v1.0 — don't keep the mic hot when the app goes to the background.
        // (The permission dialog also pauses us, so only cancel an ACTIVE listen —
        //  a pending permission request has not started the recognizer yet.)
        if (voice != null && voice.isListening()) voice.cancel();
    }

    /* ================= 1. HEADER ================= */

    private int c(int res) { return getResources().getColor(res); }

    private GradientDrawable rounded(int fill, float radiusDp, int strokeColor, float strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    private View statusDot;

    private View buildTopBar() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setBackgroundColor(c(R.color.bg));
        top.setPadding(dp(4), dp(8), dp(8), dp(8));
        top.setGravity(Gravity.CENTER_VERTICAL);

        ImageButton btnBack = new ImageButton(this);
        btnBack.setImageResource(R.drawable.ic_v2_back);
        btnBack.setBackgroundResource(R.drawable.bg_icon_chip);
        btnBack.setColorFilter(c(R.color.txt));
        btnBack.setPadding(dp(10), dp(10), dp(10), dp(10));
        btnBack.setOnClickListener(v -> finish());
        top.addView(btnBack);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(dp(4), 0, 0, 0);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("ALU AI");
        tvTitle.setTextColor(c(R.color.txt));
        // One UI screen title
        tvTitle.setTextSize(22f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setLetterSpacing(-0.02f);
        tvTitle.setIncludeFontPadding(false);
        textCol.addView(tvTitle);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusDot = new View(this);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(c(R.color.ok));
        statusDot.setBackground(dot);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(7), dp(7));
        dlp.setMarginEnd(dp(6));
        statusDot.setLayoutParams(dlp);
        statusRow.addView(statusDot);
        tvStatus = new TextView(this);
        tvStatus.setText("On-device");
        tvStatus.setTextColor(c(R.color.mut));
        tvStatus.setTextSize(12f);
        statusRow.addView(tvStatus);
        textCol.addView(statusRow);
        top.addView(textCol);

        btnThink = new TextView(this);
        btnThink.setTextSize(15f);
        btnThink.setPadding(dp(10), dp(5), dp(10), dp(5));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.setMarginEnd(dp(6));
        btnThink.setLayoutParams(tlp);
        btnThink.setOnClickListener(v -> toggleThinking());
        refreshThinkButton();
        top.addView(btnThink);

        TextView btnNew = new TextView(this);
        btnNew.setText("＋ New");
        btnNew.setTextSize(13f);
        btnNew.setTypeface(null, Typeface.BOLD);
        btnNew.setTextColor(c(R.color.accText));
        btnNew.setBackground(rounded(c(R.color.accLight), 999, 0, 0));
        btnNew.setPadding(dp(12), dp(6), dp(12), dp(6));
        btnNew.setOnClickListener(v -> clearChat());
        top.addView(btnNew);

        ImageButton btnMenu = new ImageButton(this);
        btnMenu.setImageResource(R.drawable.ic_v2_more);
        btnMenu.setBackgroundResource(R.drawable.bg_icon_chip);
        btnMenu.setColorFilter(c(R.color.txt));
        btnMenu.setPadding(dp(8), dp(8), dp(8), dp(8));
        btnMenu.setOnClickListener(this::showOverflowMenu);
        top.addView(btnMenu);
        return top;
    }

    /* ================= 2. COMPOSER ================= */

    private View buildComposer() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(12), 0, dp(12), dp(10));
        wrap.setBackgroundColor(c(R.color.bg));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.BOTTOM);
        // One UI composer: full pill, sunken fill, flat (no elevation)
        box.setBackground(rounded(c(R.color.card2), 999, 0, 0));
        box.setElevation(0f);
        box.setPadding(dp(6), dp(6), dp(6), dp(6));

        btnAttach = new ImageButton(this);
        btnAttach.setImageResource(R.drawable.ic_v2_attach);
        btnAttach.setColorFilter(c(R.color.ic));
        btnAttach.setBackground(rounded(c(R.color.card3), 999, 0, 0));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(40), dp(40));
        alp.bottomMargin = dp(1);
        btnAttach.setLayoutParams(alp);
        btnAttach.setOnClickListener(v -> showAttachmentDialog());
        box.addView(btnAttach);

        etInput = new EditText(this);
        etInput.setHint("Kaam likho… jaise 48x60 ki 2 window add karo");
        etInput.setHintTextColor(c(R.color.hint));
        etInput.setTextColor(c(R.color.txt));
        etInput.setTextSize(15.5f);
        etInput.setBackground(null);
        etInput.setPadding(dp(10), dp(10), dp(6), dp(10));
        etInput.setMaxLines(5);
        etInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(etInput);

        // v1.0 — voice input: bol kar window add / cutting plan / BOM
        btnMic = new ImageButton(this);
        btnMic.setImageResource(R.drawable.ic_v2_mic);
        btnMic.setColorFilter(c(R.color.ic));
        btnMic.setBackground(rounded(c(R.color.card3), 999, 0, 0));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(40), dp(40));
        mlp.rightMargin = dp(4);
        mlp.bottomMargin = dp(1);
        btnMic.setLayoutParams(mlp);
        btnMic.setContentDescription("Bol kar likho (voice input)");
        btnMic.setOnClickListener(v -> onMicClicked());
        box.addView(btnMic);

        btnSend = new ImageButton(this);
        btnSend.setImageResource(R.drawable.ic_v2_send);
        btnSend.setColorFilter(c(R.color.onAcc));
        btnSend.setBackground(rounded(c(R.color.acc), 999, 0, 0));
        btnSend.setPadding(dp(10), dp(10), dp(10), dp(10));
        btnSend.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        btnSend.setAlpha(0.45f);
        btnSend.setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            if (!text.isEmpty()) sendMessage(text);
        });
        box.addView(btnSend);
        wrap.addView(box);

        etInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                btnSend.setAlpha(s.toString().trim().length() > 0 ? 1f : 0.45f);
            }
        });

        TextView foot = new TextView(this);
        foot.setText("100% offline · phone ke andar chalta hai");
        foot.setTextSize(10.5f);
        foot.setTextColor(c(R.color.mut));
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(6), 0, 0);
        wrap.addView(foot);
        return wrap;
    }

    /* ================= 2b. VOICE (v1.0) ================= */

    private void onMicClicked() {
        if (voice != null && voice.isListening()) { voice.stop(); return; }
        if (thinking) {
            Toast.makeText(this, "Ruko zara — pehla jawab aa raha hai…", Toast.LENGTH_SHORT).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.RECORD_AUDIO }, REQ_MIC);
            return;
        }
        startVoice();
    }

    private void startVoice() {
        if (voice == null) {
            voice = new VoiceEngine(this);
            voice.setLanguage(VoiceEngine.forDeviceLocale());
            voice.setListener(new VoiceEngine.Listener() {
                @Override public void onListeningStarted() {
                    runOnUiThread(() -> {
                        btnMic.setColorFilter(c(R.color.onAcc));
                        btnMic.setBackground(rounded(c(R.color.acc), 999, 0, 0));
                        etInput.setHint("Sun raha hoon… bolo");
                    });
                }
                @Override public void onPartial(String text) {
                    runOnUiThread(() -> etInput.setText(text));
                }
                @Override public void onFinal(String text) {
                    runOnUiThread(() -> {
                        resetMicUi();
                        etInput.setText("");
                        sendMessage(text);
                    });
                }
                @Override public void onError(String reason) {
                    runOnUiThread(() -> {
                        resetMicUi();
                        Toast.makeText(AgentActivity.this, reason, Toast.LENGTH_LONG).show();
                    });
                }
                @Override public void onListeningStopped() {
                    runOnUiThread(AgentActivity.this::resetMicUi);
                }
            });
        }
        // Hide the keyboard — the user is about to talk, not type.
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etInput.getWindowToken(), 0);
        voice.start();
    }

    private void resetMicUi() {
        if (btnMic == null) return;
        btnMic.setColorFilter(c(R.color.ic));
        btnMic.setBackground(rounded(c(R.color.card3), 999, 0, 0));
        etInput.setHint("Kaam likho… jaise 48x60 ki 2 window add karo");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoice();
            } else {
                Toast.makeText(this,
                        "Mic permission ke bina voice nahi chalega — Settings se permission do.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /* ================= 3. SUGGESTIONS ================= */

    private static final String[][] SUGGESTIONS = {
            {"🪟", "Window add", "48x60 ki 2 window add karo"},
            {"📐", "Estimate", "Kitne pipe lagenge?"},
            {"✂️", "Cutting plan", "Cutting plan dikhao"},
            {"📦", "BOM order", "Material order (BOM) list dikhao"},
            {"💰", "Kharcha", "Total cost kitna hua?"},
            {"📋", "Windows list", "Windows list dikhao"},
            {"👤", "Customers", "Saved customer list dikhao"},
    };

    private void populateQuickChips() {
        chipRow.removeAllViews();
        for (String[] sg : SUGGESTIONS) {
            TextView chip = new TextView(this);
            chip.setText(sg[0] + "  " + sg[1]);
            chip.setTextSize(13f);
            chip.setTextColor(c(R.color.txt));
            chip.setBackground(rounded(c(R.color.card2), 999, 0, 0));
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> sendMessage(sg[2]));
            chipRow.addView(chip);
        }
    }

    /** Empty-state hero: greeting + 2-column suggestion cards (ChatGPT/Gemini style). */
    private void addWelcome() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setTag("welcome");
        hero.setPadding(0, dp(28), 0, dp(8));

        TextView hi = new TextView(this);
        hi.setText("Namaste 👋");
        hi.setTextSize(28f);
        hi.setTypeface(null, Typeface.BOLD);
        hi.setTextColor(c(R.color.acc));
        hero.addView(hi);

        TextView sub = new TextView(this);
        sub.setText("Aaj workshop me kya kaam karna hai?");
        sub.setTextSize(20f);
        sub.setTextColor(c(R.color.mut));
        sub.setPadding(0, dp(2), 0, dp(18));
        hero.addView(sub);

        String status = "";
        try {
            OnDeviceTextGenerator.ModelResponse g = engine.getGreetingResponse();
            int k = g.text.indexOf("Current Sheet Status:");
            if (k >= 0) {
                String t = g.text.substring(k + "Current Sheet Status:".length()).trim();
                int nl = t.indexOf('\n');
                status = nl > 0 ? t.substring(0, nl) : t;
            }
        } catch (Throwable ignored) { }
        if (status.length() > 0) {
            TextView st = new TextView(this);
            st.setText("📊 Sheet: " + status);
            st.setTextSize(13f);
            st.setTextColor(c(R.color.txt));
            st.setBackground(rounded(c(R.color.accLight), 12, 0, 0));
            st.setPadding(dp(12), dp(8), dp(12), dp(8));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.bottomMargin = dp(14);
            st.setLayoutParams(slp);
            hero.addView(st);
        }

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i + 1 < SUGGESTIONS.length; i += 2) {
            LinearLayout rowL = new LinearLayout(this);
            rowL.setOrientation(LinearLayout.HORIZONTAL);
            rowL.addView(suggestionCard(SUGGESTIONS[i], true));
            rowL.addView(suggestionCard(SUGGESTIONS[i + 1], false));
            grid.addView(rowL);
        }
        hero.addView(grid);
        chatBox.addView(hero);
    }

    private View suggestionCard(String[] sg, boolean left) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(c(R.color.card), 26, c(R.color.line), 1));
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(left ? 0 : dp(5), 0, left ? dp(5) : 0, dp(10));
        card.setLayoutParams(lp);
        TextView ic = new TextView(this);
        ic.setText(sg[0]);
        ic.setTextSize(22f);
        card.addView(ic);
        TextView t = new TextView(this);
        t.setText(sg[1]);
        t.setTextSize(14f);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(c(R.color.txt));
        t.setPadding(0, dp(6), 0, 0);
        card.addView(t);
        TextView d = new TextView(this);
        d.setText(sg[2]);
        d.setTextSize(12f);
        d.setTextColor(c(R.color.mut));
        card.addView(d);
        card.setOnClickListener(v -> sendMessage(sg[2]));
        return card;
    }

    private void removeWelcome() {
        for (int i = chatBox.getChildCount() - 1; i >= 0; i--) {
            if ("welcome".equals(chatBox.getChildAt(i).getTag())) chatBox.removeViewAt(i);
        }
        if (chipScrollView != null) chipScrollView.setVisibility(View.GONE);
    }

    private void clearChat() {
        chatBox.removeAllViews();
        messages.clear();
        getSharedPreferences("alu_ai_ui", MODE_PRIVATE).edit().putLong("setup_hint_at", 0L).apply();
        if (chipScrollView != null) chipScrollView.setVisibility(View.VISIBLE);
        addWelcome();
    }

    /* ================= 5. MESSAGE HANDLING ================= */

    private void sendMessage(String text) {
        if (text == null || text.trim().isEmpty()) return;
        text = text.trim();

        // One request at a time: a slow on-device generation must not race with
        // a second tap on the same model handle.
        if (thinking) {
            Toast.makeText(this, "Ruko zara — pehla jawab aa raha hai…", Toast.LENGTH_SHORT).show();
            return;
        }

        addUserMessage(text);
        etInput.setText("");

        // Hide soft keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etInput.getWindowToken(), 0);

        // Show typing indicator
        setTypingStatus(true);
        addTypingIndicator();

        thinking = true;
        final String userPrompt = text;

        // The brain runs OFF the UI thread: a cloud call or a local MNN generation
        // can take seconds, and blocking here would freeze the app.
        new Thread(() -> {
            HybridAgent.Result res;
            try {
                if (hybrid == null) hybrid = new HybridAgent(AgentActivity.this);
                hybrid.reloadConfig();
                hybrid.setProgress(new HybridAgent.Progress() {
                    @Override public void onToken(String piece, int count) {
                        typingTokens = count;
                        synchronized (liveRawBuf) { liveRawBuf.append(piece); }
                        postLive();
                    }
                    @Override public void onThink(String piece) {
                        synchronized (liveThinkBuf) { liveThinkBuf.append(piece); }
                        postLive();
                    }
                });
                res = hybrid.handle(userPrompt);
            } catch (Throwable t) {
                res = new HybridAgent.Result();
                res.text = "Sorry, agent me dikkat aa gayi. Dobara try karein.";
                res.source = HybridAgent.SRC_OFFLINE;
                res.note = "";
            }
            final HybridAgent.Result out = res;
            runOnUiThread(() -> {
                thinking = false;
                if (!alive()) return;
                try {
                    String th;
                    synchronized (liveThinkBuf) { th = liveThinkBuf.toString(); }
                    pendingThink = th.replace("<think>", "").replace("</think>", "").trim();
                    removeTypingIndicator();
                    setTypingStatus(false);

                    String body = out.text == null ? "" : out.text;
                    if (HybridAgent.SRC_CLOUD.equals(out.source)) body = "☁️ " + body;

                    if (out.note != null && out.note.length() > 0 && !"action".equals(out.note)) {
                        body = body + "\n\n_" + out.note + "_";
                    }
                    addAgentMessage(body, out.actions);
                    updateBrainStatus();
                } catch (Throwable ignored) { }
            });
        }, "alu-agent").start();
    }

    /** Header subtitle tells the truth about which brain is live. */
    private void updateBrainStatus() {
        if (tvStatus == null) return;
        try {
            if (hybrid == null) hybrid = new HybridAgent(this);
            hybrid.reloadConfig();
            tvStatus.setText(hybrid.statusLine());
        } catch (Throwable ignored) { }
    }

    private boolean alive() {
        if (isFinishing()) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed()) return false;
        return true;
    }

    /** First run: one clear message — download the model, that's the whole setup. */
    private void maybeShowSetupGuidance() {
        try {
            ModelCatalog.ModelSpec m = ModelCatalog.DEFAULT();
            if (ModelStore.isInstalled(this, m)) return;
            android.content.SharedPreferences p = getSharedPreferences("alu_ai_ui", MODE_PRIVATE);
            long last = p.getLong("setup_hint_at", 0L);
            if (System.currentTimeMillis() - last < 6L * 60L * 60L * 1000L) return;
            p.edit().putLong("setup_hint_at", System.currentTimeMillis()).apply();
            List<ChatMessage.Action> acts = new ArrayList<>();
            acts.add(new ChatMessage.Action("⬇ MODEL DOWNLOAD KARO (" + m.sizeText() + ")", "DOWNLOAD_MODEL"));
            addAgentMessage("🧠 Ek baar AI model download karo (" + m.sizeText() + ", WiFi par behtar). "
                    + "Uske baad sab offline chalega — aap seedha mujhse baat karoge aur main app ka "
                    + "kaam (window add, estimate, cutting, price) khud kar dunga.", acts);
        } catch (Throwable ignored) { }
    }

    private void addUserMessage(String text) {
        messages.add(new ChatMessage(ChatMessage.ROLE_USER, text));
        removeWelcome();

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        row.setPadding(dp(48), dp(6), 0, dp(6));

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15.5f);
        // Samsung Messages style: outgoing bubble violet, label white
        tv.setTextColor(c(R.color.onAcc));
        tv.setLineSpacing(dp(2), 1f);
        tv.setBackground(rounded(c(R.color.acc), 22, 0, 0));
        tv.setPadding(dp(16), dp(10), dp(16), dp(10));
        row.addView(tv);
        chatBox.addView(row);
        scrollToBottom();
    }

    /** Small round "AI" badge at the left of every assistant answer. */
    private View aiBadge() {
        TextView b = new TextView(this);
        b.setText("✦");
        b.setTextSize(13f);
        b.setTextColor(c(R.color.onAcc));
        b.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColors(new int[]{c(R.color.acc), c(R.color.cRp)});
        g.setOrientation(GradientDrawable.Orientation.TL_BR);
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(26), dp(26));
        lp.setMarginEnd(dp(10));
        lp.topMargin = dp(2);
        b.setLayoutParams(lp);
        return b;
    }

    private void addAgentMessage(String text, List<ChatMessage.Action> actions) {
        messages.add(new ChatMessage(ChatMessage.ROLE_AGENT, text, actions));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.START);
        row.setPadding(0, dp(8), dp(4), dp(8));
        row.addView(aiBadge());

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 💭 collapsed thinking (tap to expand) — kept so the user can see WHY
        if (pendingThink != null && pendingThink.length() > 0) {
            final String full = pendingThink;
            pendingThink = "";
            final TextView th = new TextView(this);
            th.setTextSize(13f);
            th.setTextColor(c(R.color.mut));
            th.setTypeface(null, Typeface.ITALIC);
            th.setBackground(rounded(c(R.color.card2), 12, c(R.color.line), 1));
            th.setPadding(dp(12), dp(6), dp(12), dp(6));
            th.setMaxLines(1);
            th.setEllipsize(android.text.TextUtils.TruncateAt.END);
            th.setText("💭 Soch: " + full.replace('\n', ' ') + "  ▸");
            LinearLayout.LayoutParams thlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            thlp.bottomMargin = dp(6);
            th.setLayoutParams(thlp);
            final boolean[] open = {false};
            th.setOnClickListener(v -> {
                open[0] = !open[0];
                th.setMaxLines(open[0] ? 200 : 1);
                th.setText(open[0] ? "💭 Soch:\n" + full + "  ▾" : "💭 Soch: " + full.replace('\n', ' ') + "  ▸");
            });
            col.addView(th);
        }

        // Answers are full-width text (no bubble) — like Gemini / ChatGPT.
        TextView tv = new TextView(this);
        tv.setText(formatMarkdown(text));
        tv.setTextSize(15.5f);
        tv.setTextColor(c(R.color.txt));
        tv.setLineSpacing(dp(3), 1f);
        tv.setTextIsSelectable(true);
        col.addView(tv);

        if (actions != null && !actions.isEmpty()) {
            HorizontalScrollView actionScroll = new HorizontalScrollView(this);
            actionScroll.setHorizontalScrollBarEnabled(false);
            actionScroll.setPadding(0, dp(10), 0, dp(2));
            LinearLayout actionRow = new LinearLayout(this);
            actionRow.setOrientation(LinearLayout.HORIZONTAL);
            for (ChatMessage.Action act : actions) {
                TextView actBtn = new TextView(this);
                actBtn.setText(act.title);
                actBtn.setTextSize(13f);
                actBtn.setTypeface(null, Typeface.BOLD);
                actBtn.setTextColor(c(R.color.accText));
                actBtn.setBackground(rounded(c(R.color.accLight), 999, 0, 0));
                actBtn.setPadding(dp(14), dp(7), dp(14), dp(7));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMarginEnd(dp(8));
                actBtn.setLayoutParams(lp);
                actBtn.setOnClickListener(v -> executeAction(act));
                actionRow.addView(actBtn);
            }
            actionScroll.addView(actionRow);
            col.addView(actionScroll);
        }

        row.addView(col);
        chatBox.addView(row);
        scrollToBottom();
    }

    private void addTypingIndicator() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), dp(4), dp(8));
        row.setTag("typing");
        row.addView(aiBadge());

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView text = new TextView(this);
        text.setText("Soch raha hai…");
        text.setTextSize(13f);
        text.setTextColor(c(R.color.mut));
        text.setTag("typing_text");
        col.addView(text);

        // 💭 live thinking (grey italic card) — only visible when the model thinks
        liveThink = new TextView(this);
        liveThink.setTextSize(13.5f);
        liveThink.setTextColor(c(R.color.mut));
        liveThink.setTypeface(null, Typeface.ITALIC);
        liveThink.setLineSpacing(dp(2), 1f);
        liveThink.setBackground(rounded(c(R.color.card2), 12, c(R.color.line), 1));
        liveThink.setPadding(dp(12), dp(8), dp(12), dp(8));
        liveThink.setMaxLines(6);
        liveThink.setEllipsize(android.text.TextUtils.TruncateAt.START);
        LinearLayout.LayoutParams thlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        thlp.topMargin = dp(6);
        liveThink.setLayoutParams(thlp);
        liveThink.setVisibility(View.GONE);
        col.addView(liveThink);

        // live answer text (streams word by word)
        liveAnswer = new TextView(this);
        liveAnswer.setTextSize(15.5f);
        liveAnswer.setTextColor(c(R.color.txt));
        liveAnswer.setLineSpacing(dp(3), 1f);
        liveAnswer.setPadding(0, dp(6), 0, 0);
        liveAnswer.setVisibility(View.GONE);
        col.addView(liveAnswer);

        row.addView(col);
        row.setOnClickListener(v -> { if (hybrid != null) hybrid.cancel(); });
        typingText = text;
        synchronized (liveThinkBuf) { liveThinkBuf.setLength(0); }
        synchronized (liveRawBuf) { liveRawBuf.setLength(0); }
        typingStart = System.currentTimeMillis();
        typingTokens = 0;
        ui.postDelayed(typingTick, 1000);

        chatBox.addView(row);
        scrollToBottom();
    }

    private TextView typingText;
    private long typingStart;
    private volatile int typingTokens;
    private final Runnable typingTick = new Runnable() {
        @Override public void run() {
            if (typingText == null || !thinking) return;
            long sec = (System.currentTimeMillis() - typingStart) / 1000;
            String dots = sec % 3 == 0 ? "." : sec % 3 == 1 ? ".." : "...";
            String t = (typingTokens > 0 ? "Likh raha hai" + dots + "  " + typingTokens + " tokens"
                    : sec > 5 ? "Prompt padh raha hai" + dots : "Soch raha hai" + dots) + "  · " + sec + "s";
            if (sec > 30) t += "  (tap = cancel)";
            typingText.setText(t);
            ui.postDelayed(this, 1000);
        }
    };

    /** Throttled UI refresh of the live bubble (max ~8 fps). */
    private void postLive() {
        if (livePosted) return;
        livePosted = true;
        ui.postDelayed(() -> {
            livePosted = false;
            if (liveThink == null || liveAnswer == null || !thinking) return;
            String th; String raw;
            synchronized (liveThinkBuf) { th = liveThinkBuf.toString(); }
            synchronized (liveRawBuf) { raw = liveRawBuf.toString(); }
            th = th.replace("<think>", "").replace("</think>", "").trim();
            if (th.length() > 0) {
                liveThink.setText("💭 " + th);
                if (liveThink.getVisibility() != View.VISIBLE) liveThink.setVisibility(View.VISIBLE);
            }
            String say = liveSay(raw);
            if (say.length() > 0) {
                liveAnswer.setText(say);
                if (liveAnswer.getVisibility() != View.VISIBLE) liveAnswer.setVisibility(View.VISIBLE);
            }
            scrollToBottom();
        }, 120);
    }

    /** What the user should see while the JSON is still being written. */
    static String liveSay(String raw) {
        if (raw == null) return "";
        int k = raw.indexOf("\"say\":");
        if (k >= 0) {
            int q = raw.indexOf('"', k + 6);
            if (q < 0) return "";
            String s = raw.substring(q + 1);
            // stop at the closing quote of "say"
            StringBuilder sb = new StringBuilder();
            boolean esc = false;
            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                if (esc) { sb.append(ch == 'n' ? '\n' : ch); esc = false; continue; }
                if (ch == '\\') { esc = true; continue; }
                if (ch == '"') break;
                sb.append(ch);
            }
            return sb.toString();
        }
        int t = raw.indexOf("\"tool\":\"");
        if (t >= 0) {
            int e = raw.indexOf('"', t + 8);
            if (e > t + 8) return "⚙ " + raw.substring(t + 8, e).replace('_', ' ') + " chala raha hoon…";
        }
        return "";
    }

    private void toggleThinking() {
        try {
            AiSettings s = AiSettings.load(this);
            s.thinking = !s.thinking;
            s.save(this);
            refreshThinkButton();
            Toast.makeText(this, s.thinking
                    ? "💭 Thinking ON — model pehle sochega (thoda dheema, zyada sahi)"
                    : "⚡ Thinking OFF — model seedha jawab dega (tez)", Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) { }
    }

    private void refreshThinkButton() {
        if (btnThink == null) return;
        boolean on = false;
        try { on = AiSettings.load(this).thinking; } catch (Throwable ignored) { }
        btnThink.setText(on ? "💭" : "⚡");
        btnThink.setBackground(rounded(on ? c(R.color.accLight) : c(R.color.card2), 999,
                on ? c(R.color.acc) : c(R.color.line), on ? 0 : 1));
        btnThink.setContentDescription(on ? "Thinking ON" : "Thinking OFF");
    }

    private void removeTypingIndicator() {
        typingText = null;
        liveThink = null; liveAnswer = null;
        ui.removeCallbacks(typingTick);
        for (int i = chatBox.getChildCount() - 1; i >= 0; i--) {
            View v = chatBox.getChildAt(i);
            if ("typing".equals(v.getTag())) {
                chatBox.removeViewAt(i);
                break;
            }
        }
    }

    private void setTypingStatus(boolean typing) {
        if (tvStatus != null) {
            tvStatus.setText(typing ? "likh raha hai…" : "On-device AI");
            tvStatus.setTextColor(c(R.color.mut));
        }
    }

    /* ================= 6. ACTION DISPATCHER ================= */

    private void executeAction(ChatMessage.Action action) {
        switch (action.type) {
            case "ESTIMATE":
            case "WINDOWS":
                // Return to MainActivity which refreshes on onResume
                Toast.makeText(this, "Opening Main Sheet...", Toast.LENGTH_SHORT).show();
                finish();
                break;

            case "CUTTING":
            case "MANUAL_PCO":
                startActivity(new Intent(this, ManualCuttingActivity.class));
                break;

            case "PRICE":
                startActivity(new Intent(this, PriceActivity.class));
                break;

            case "CUSTOMERS":
                startActivity(new Intent(this, CustomerActivity.class));
                break;

            case "SHARE_WHATSAPP":
                shareSummaryToWhatsApp();
                break;

            case "ADD_WINDOW":
                if (action.payload != null && !action.payload.isEmpty()) {
                    String[] parts = action.payload.split(",");
                    if (parts.length >= 5) {
                        try {
                            double h = Double.parseDouble(parts[0]);
                            double w = Double.parseDouble(parts[1]);
                            int s = Integer.parseInt(parts[2]);
                            int n = Integer.parseInt(parts[3]);
                            int sys = Integer.parseInt(parts[4]);
                            engine.getController().addWindow(h, w, s, n, sys, "");
                            sendMessage("Estimate sheet status dikhao");
                        } catch (Exception e) {
                            Toast.makeText(this, "Error adding window", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
                break;

            case "CLEAR_ALL":
                sendMessage("Sab windows clear kardo");
                break;

            case "AI_SETUP":
            case "MODEL_TEST":
            case "CLOUD_KEY":
            case "DOWNLOAD_MODEL":
                startModelDownload();
                break;

            case "OFFLINE_MODE":
                try {
                    AiSettings cfgo = AiSettings.load(this);
                    cfgo.mode = AiSettings.MODE_OFFLINE_ONLY;
                    cfgo.save(this);
                    updateBrainStatus();
                    addAgentMessage("Theek hai — internet AI band. On-device model aur rules chalenge. "
                            + "Kabhi bhi ⋮ menu → AI Setup se badal sakte hai.", null);
                } catch (Throwable t) {
                    Toast.makeText(this, "Mode set nahi ho paya", Toast.LENGTH_SHORT).show();
                }
                break;

            default:
                if (action.payload != null && !action.payload.isEmpty()) {
                    sendMessage(action.payload);
                }
                break;
        }
    }

    private void shareSummaryToWhatsApp() {
        try {
            OnDeviceTextGenerator.ModelResponse est = engine.process("estimate");
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, est.text);
            startActivity(Intent.createChooser(share, "Share via WhatsApp"));
        } catch (Exception e) {
            Toast.makeText(this, "Could not open share dialog", Toast.LENGTH_SHORT).show();
        }
    }

    /* ================= 7. ATTACHMENT DIALOG & OVERFLOW ================= */

    private void showAttachmentDialog() {
        String[] options = {
                "🪟 Add Quick 48×60 Window",
                "📊 View Current Estimate",
                "✂ View Pipe Cutting Plan",
                "💰 Open Price Book",
                "👤 Customer Records",
                "📐 Manual PCO & Sheet Cutting",
                "🧹 Clear All Windows"
        };

        new AlertDialog.Builder(this)
                .setTitle("Quick AI Actions")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: sendMessage("48x60 ki 2 window add karo"); break;
                        case 1: sendMessage("Estimate dikhao"); break;
                        case 2: sendMessage("Cutting plan dikhao"); break;
                        case 3: startActivity(new Intent(this, PriceActivity.class)); break;
                        case 4: startActivity(new Intent(this, CustomerActivity.class)); break;
                        case 5: startActivity(new Intent(this, ManualCuttingActivity.class)); break;
                        case 6: sendMessage("Sab windows clear kardo"); break;
                    }
                })
                .show();
    }

    private void showOverflowMenu(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        popup.getMenu().add("📊 View Main Sheet");
        popup.getMenu().add("✂ Cutting Optimization");
        popup.getMenu().add("💰 Price Setup");
        popup.getMenu().add("👤 Customer Records");
        popup.getMenu().add("🧠 AI Model status");
        popup.getMenu().add("🧹 Clear Chat");

        popup.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.contains("Main Sheet")) {
                finish();
            } else if (title.contains("Cutting")) {
                startActivity(new Intent(this, ManualCuttingActivity.class));
            } else if (title.contains("Price")) {
                startActivity(new Intent(this, PriceActivity.class));
            } else if (title.contains("Customer")) {
                startActivity(new Intent(this, CustomerActivity.class));
            } else if (title.contains("AI Model")) {
                showModelStatusDialog();
            } else if (title.contains("Clear Chat")) {
                clearChat();
            }
            return true;
        });
        popup.show();
    }

    /* ================= 8. UTILITIES ================= */

    private void scrollToBottom() {
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** Simple markdown parser for *bold* text */
    private CharSequence formatMarkdown(String raw) {
        if (raw == null) return "";
        SpannableStringBuilder sb = new SpannableStringBuilder();
        String[] lines = raw.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int start = sb.length();

            // Check bold pairs: *text*
            int p = 0;
            while (p < line.length()) {
                int firstStar = line.indexOf('*', p);
                if (firstStar != -1) {
                    int secondStar = line.indexOf('*', firstStar + 1);
                    if (secondStar != -1) {
                        sb.append(line.substring(p, firstStar));
                        int boldStart = sb.length();
                        sb.append(line.substring(firstStar + 1, secondStar));
                        sb.setSpan(new StyleSpan(Typeface.BOLD), boldStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        p = secondStar + 1;
                    } else {
                        sb.append(line.substring(p));
                        break;
                    }
                } else {
                    sb.append(line.substring(p));
                    break;
                }
            }

            if (i < lines.length - 1) {
                sb.append("\n");
            }
        }
        return sb;
    }

    /* ================= 9. SINGLE MODEL: DOWNLOAD / LOAD ================= */

    private View buildModelBar() {
        modelBar = new LinearLayout(this);
        modelBar.setOrientation(LinearLayout.VERTICAL);
        modelBar.setPadding(dp(14), dp(12), dp(14), dp(12));
        modelBar.setBackground(rounded(c(R.color.card), 26, c(R.color.line), 1));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.setMargins(dp(12), 0, dp(12), dp(8));
        modelBar.setLayoutParams(mlp);
        modelBar.setVisibility(View.GONE);

        modelText = new TextView(this);
        modelText.setTextColor(getResources().getColor(R.color.txt));
        modelText.setTextSize(13f);
        modelBar.addView(modelText);

        modelProgress = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        modelProgress.setMax(100);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        plp.topMargin = dp(6);
        modelProgress.setLayoutParams(plp);
        modelBar.addView(modelProgress);

        modelBtn = new Button(this);
        modelBtn.setAllCaps(false);
        modelBtn.setTextColor(c(R.color.onAcc));
        modelBtn.setBackground(rounded(c(R.color.acc), 999, 0, 0));
        modelBtn.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(46));
        blp.topMargin = dp(10);
        modelBtn.setLayoutParams(blp);
        modelBtn.setPadding(dp(18), 0, dp(18), 0);
        modelBtn.setOnClickListener(v -> startModelDownload());
        modelBar.addView(modelBtn);
        return modelBar;
    }

    /** Decide what the banner shows: download button, progress, loading, or nothing. */
    private boolean cleanedOld = false;
    private boolean autoResumed = false;

    private void refreshModelBar() {
        if (modelBar == null) return;
        try {
            if (!cleanedOld) { cleanedOld = true; ModelStore.deleteOthers(this); }
            ModelCatalog.ModelSpec m = ModelCatalog.DEFAULT();
            com.digitalalu.alu.ai.LocalModelHost host = com.digitalalu.alu.ai.LocalModelHost.get();
            if (ModelStore.isInstalled(this, m)) {
                ModelStore.forget(this, m.id);
                if (host.isReady()) {
                    modelBar.setVisibility(View.GONE);
                    modelPolling = false;
                    updateBrainStatus();
                    return;
                }
                // installed but not loaded yet -> load now, show "loading"
                if (!host.runtimeAvailable()) {
                    show("⚠️ AI runtime load nahi hua (" + Build.SUPPORTED_ABIS[0] + "): "
                            + com.digitalalu.alu.ai.LocalLlm.unavailableReason()
                            + "\nBasic commands phir bhi chalenge.", false, 0, null);
                    return;
                }
                String err = host.lastError();
                if (host.state() == com.digitalalu.alu.ai.AiReadiness.STATE_FAILED && err != null && err.length() > 0) {
                    show("⚠️ Model load nahi hua: " + err, false, 0, "🔁 DOBARA LOAD");
                    modelBtn.setOnClickListener(v -> { host.unload(); loadModel(); });
                    return;
                }
                show("🧠 AI taiyaar ho raha hai… sirf PEHLI baar ~1 min (weight + prompt cache disk par ban rahi hai), agli baar se ~10 s", true, -1, null);
                loadModel();
                pollModel();
                return;
            }
            long id = ModelStore.downloadIdFor(this, m.id);
            if (id > 0) {
                ModelStore.Progress pr = ModelStore.progress(this, id, m);
                if (pr.failed) {
                    if (!autoResumed && pr.bytes > 0 && (pr.error == null || pr.error.length() == 0)) {
                        // process was killed mid-download (no error recorded): just continue
                        autoResumed = true;
                        ModelStore.forget(this, m.id);
                        startModelDownload();
                        return;
                    }
                    ModelStore.forget(this, m.id);
                    String why = pr.error != null && pr.error.length() > 0 ? " (" + pr.error + ")" : "";
                    show("❌ Download ruk gaya" + why + ". " + pr.percent + "% save hai — RESUME dabao, wahin se chalega.",
                            false, pr.percent, "⬇ RESUME (" + pr.percent + "%)");
                    modelBtn.setOnClickListener(v -> startModelDownload());
                    return;
                }
                if (pr.done) { refreshModelBar(); return; }
                show("⬇ AI model download: " + pr.percent + "%  •  " + pr.statusText(), true, pr.percent, "✖ CANCEL");
                modelBtn.setOnClickListener(v -> {
                    ModelStore.cancelModel(this, m);
                    ModelStore.forget(this, m.id);
                    refreshModelBar();
                });
                pollModel();
                return;
            }
            show("🧠 AI model abhi download nahi hua (" + m.sizeText() + ", ek baar). "
                    + "Tab tak sirf basic commands chalenge.", false, 0, "⬇ DOWNLOAD (" + m.sizeText() + ")");
            modelBtn.setOnClickListener(v -> startModelDownload());
        } catch (Throwable ignored) { }
    }

    private void show(String text, boolean progress, int percent, String btn) {
        modelBar.setVisibility(View.VISIBLE);
        modelText.setText(text);
        modelProgress.setVisibility(progress ? View.VISIBLE : View.GONE);
        modelProgress.setIndeterminate(percent < 0);
        if (percent >= 0) modelProgress.setProgress(percent);
        modelBtn.setVisibility(btn == null ? View.GONE : View.VISIBLE);
        if (btn != null) modelBtn.setText(btn);
    }

    private void pollModel() {
        if (modelPolling) return;
        modelPolling = true;
        ui.postDelayed(new Runnable() {
            @Override public void run() {
                if (!alive() || !modelPolling) return;
                modelPolling = false;
                refreshModelBar();
                if (modelBar.getVisibility() == View.VISIBLE && modelProgress.getVisibility() == View.VISIBLE) {
                    pollModel();
                }
            }
        }, 1000);
    }

    private void loadModel() {
        try {
            if (hybrid == null) hybrid = new HybridAgent(this);
            hybrid.reloadConfig();
            com.digitalalu.alu.ai.LocalModelHost.get().ensureLoaded(this, hybrid.config());
        } catch (Throwable ignored) { }
    }

    private void startModelDownload() { startModelDownload(ModelCatalog.DEFAULT()); }

    /** v1.1 — kisi bhi catalog model ka download (MNN ya FunctionGemma). */
    private void startModelDownload(ModelCatalog.ModelSpec m) {
        try {
            if (m == null) m = ModelCatalog.DEFAULT();
            if (ModelStore.isInstalled(this, m)) { refreshModelBar(); return; }
            if (ModelStore.downloadIdFor(this, m.id) > 0) { refreshModelBar(); return; }
            if (!ModelStore.storageOk(this, m)) {
                Toast.makeText(this, "Phone me jagah kam hai — kam se kam 1 GB free karo", Toast.LENGTH_LONG).show();
                return;
            }
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                try { requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 77); } catch (Throwable ignored) { }
            }
            if (System.currentTimeMillis() - lastDownloadTap < 4000L) return;   // double tap guard
            lastDownloadTap = System.currentTimeMillis();
            long id = ModelStore.enqueue(this, m, false);
            if (id == -1) {
                Toast.makeText(this, "Download shuru nahi hua — internet check karo", Toast.LENGTH_LONG).show();
                return;
            }
            addAgentMessage("⬇ Model download shuru (" + m.sizeText() + ") — app ke andar hi, notification me bhi dikhega. Upar progress dikhegi; "
                    + "poora hote hi main khud load ho jaunga. Tab tak app use kar sakte ho.", null);
            refreshModelBar();
        } catch (Throwable t) {
            Toast.makeText(this, "Download error: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showModelStatusDialog() {
        try {
            com.digitalalu.alu.ai.LocalModelHost host = com.digitalalu.alu.ai.LocalModelHost.get();

            LinearLayout list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setPadding(dp(20), dp(14), dp(20), dp(4));

            TextView head = new TextView(this);
            head.setText("Ek ya dono model download kar sakte ho. FunctionGemma installed hone par "
                    + "tool-calling wahi karta hai (tez + pakka); MNN fallback rehta hai.");
            head.setTextSize(12.5f);
            head.setTextColor(c(R.color.mut));
            head.setPadding(0, 0, 0, dp(10));
            list.addView(head);

            for (final ModelCatalog.ModelSpec m : ModelCatalog.list()) {
                final boolean inst = ModelStore.isInstalled(this, m);
                final boolean fg = "litertlm".equals(m.runtime);
                long dlId = ModelStore.downloadIdFor(this, m.id);

                String status;
                if (inst) {
                    status = fg
                            ? "✅ Downloaded — ⚡ tool-calling tier ACTIVE"
                            : "✅ Downloaded" + (host.isReady()
                                    ? " — loaded (" + host.loadMs() + " ms)" : "");
                } else if (dlId > 0) {
                    ModelStore.Progress pr = ModelStore.progress(this, dlId, m);
                    status = pr.failed ? "⚠️ Download fail — tap to retry"
                            : "⬇ Downloading… " + pr.percent + "%";
                } else {
                    status = "⬇ Tap to download";
                }

                TextView row = new TextView(this);
                row.setText((fg ? "⚡ " : "🧠 ") + m.title + "\n"
                        + m.sizeText() + " · " + status);
                row.setTextSize(14f);
                row.setTextColor(c(R.color.txt));
                row.setLineSpacing(dp(2), 1f);
                row.setPadding(dp(14), dp(12), dp(14), dp(12));
                row.setBackground(rounded(c(R.color.card2), 16, 0, 0));
                LinearLayout.LayoutParams lp =
                        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(8);
                row.setLayoutParams(lp);

                row.setOnClickListener(v -> {
                    if (!inst) { startModelDownload(m); return; }
                    String nm = m.title;
                    new AlertDialog.Builder(AgentActivity.this)
                            .setTitle("🗑 " + nm)
                            .setMessage("Model delete karke dobara download karna hai?")
                            .setPositiveButton("Delete", (d, w) -> {
                                if (fg) com.digitalalu.alu.ai.FunctionGemmaHost.get().close();
                                else { host.unload(); }
                                ModelStore.delete(AgentActivity.this, m);
                                ModelStore.forget(AgentActivity.this, m.id);
                                refreshModelBar();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });
                list.addView(row);
            }

            new AlertDialog.Builder(this)
                    .setTitle("🧠 AI Models")
                    .setView(list)
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Throwable ignored) { }
    }

    @Override
    protected void onDestroy() {
        modelPolling = false;
        ui.removeCallbacksAndMessages(null);
        if (voice != null) { voice.destroy(); voice = null; }   // v1.0 — release mic
        super.onDestroy();
    }
}
