package com.digitalalu.alu;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.digitalalu.alu.agent.AgentEngine;
import com.digitalalu.alu.agent.OnDeviceTextGenerator;
import com.digitalalu.alu.model.ChatMessage;
import com.digitalalu.alu.ui.InsetsHelper;

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
    private TextView tvStatus;
    private HorizontalScrollView chipScrollView;
    private LinearLayout chipRow;

    private AgentEngine engine;
    private final List<ChatMessage> messages = new ArrayList<>();
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        engine = new AgentEngine(this);

        // Main WhatsApp container
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getResources().getColor(R.color.wa_bg));

        // 1. WhatsApp Top Bar
        View topBar = buildWhatsAppTopBar();
        root.addView(topBar);

        // 2. Chat Scroll Area
        scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);

        chatBox = new LinearLayout(this);
        chatBox.setOrientation(LinearLayout.VERTICAL);
        chatBox.setPadding(dp(10), dp(8), dp(10), dp(8));
        scrollView.addView(chatBox);
        root.addView(scrollView);

        // 3. Quick Suggestions Carousel (Pill Chips)
        chipScrollView = new HorizontalScrollView(this);
        chipScrollView.setHorizontalScrollBarEnabled(false);
        chipScrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        chipScrollView.setBackgroundColor(0x10000000);

        chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(dp(8), dp(6), dp(8), dp(6));
        chipScrollView.addView(chipRow);
        root.addView(chipScrollView);

        // 4. WhatsApp Bottom Input Bar
        View inputBar = buildWhatsAppInputBar();
        root.addView(inputBar);

        setContentView(root);
        InsetsHelper.apply(root, topBar);

        // Add Date & Privacy Pill
        addDateAndPrivacyBadge();

        // Populate Quick Chips
        populateQuickChips();

        // Initial Greeting
        OnDeviceTextGenerator.ModelResponse greeting = engine.getGreetingResponse();
        addAgentMessage(greeting.text, greeting.actions);
    }

    /* ================= 1. WHATSAPP TOP BAR ================= */

    private View buildWhatsAppTopBar() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setBackgroundColor(getResources().getColor(R.color.wa_teal));
        top.setPadding(dp(6), dp(10), dp(10), dp(10));
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setElevation(dp(4));

        // Back button
        ImageButton btnBack = new ImageButton(this);
        btnBack.setImageResource(R.drawable.ic_wa_back);
        btnBack.setBackgroundResource(android.R.drawable.list_selector_background);
        btnBack.setColorFilter(Color.WHITE);
        btnBack.setPadding(dp(8), dp(8), dp(4), dp(8));
        btnBack.setOnClickListener(v -> finish());
        top.addView(btnBack);

        // Agent Avatar Container
        LinearLayout avatarContainer = new LinearLayout(this);
        avatarContainer.setOrientation(LinearLayout.HORIZONTAL);
        avatarContainer.setGravity(Gravity.CENTER_VERTICAL);
        avatarContainer.setPadding(dp(2), 0, dp(8), 0);

        ImageView ivAvatar = new ImageView(this);
        ivAvatar.setImageResource(R.drawable.ic_ai_agent);
        ivAvatar.setLayoutParams(new LinearLayout.LayoutParams(dp(38), dp(38)));
        ivAvatar.setPadding(dp(6), dp(6), dp(6), dp(6));

        GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setShape(GradientDrawable.OVAL);
        avatarBg.setColor(getResources().getColor(R.color.wa_green));
        avatarBg.setStroke(dp(1.5f), Color.WHITE);
        ivAvatar.setBackground(avatarBg);
        avatarContainer.addView(ivAvatar);
        top.addView(avatarContainer);

        // Title and Status
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("ALU AI Agent 🤖");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(16.5f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        textCol.addView(tvTitle);

        tvStatus = new TextView(this);
        tvStatus.setText("online • On-Device AI");
        tvStatus.setTextColor(0xD0FFFFFF);
        tvStatus.setTextSize(11.5f);
        textCol.addView(tvStatus);

        top.addView(textCol);

        // Overflow Menu button (Three Dots)
        ImageButton btnMenu = new ImageButton(this);
        btnMenu.setImageResource(android.R.drawable.ic_menu_more);
        btnMenu.setBackgroundResource(android.R.drawable.list_selector_background);
        btnMenu.setColorFilter(Color.WHITE);
        btnMenu.setPadding(dp(8), dp(8), dp(8), dp(8));
        btnMenu.setOnClickListener(this::showOverflowMenu);
        top.addView(btnMenu);

        return top;
    }

    /* ================= 2. WHATSAPP BOTTOM INPUT BAR ================= */

    private View buildWhatsAppInputBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(Color.TRANSPARENT);
        bar.setPadding(dp(6), dp(4), dp(6), dp(8));
        bar.setGravity(Gravity.BOTTOM);

        // Pill Input Container
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setBackgroundResource(R.drawable.bg_wa_pill_input);
        pill.setPadding(dp(10), dp(4), dp(10), dp(4));
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Emoji Icon
        TextView tvEmoji = new TextView(this);
        tvEmoji.setText("😊");
        tvEmoji.setTextSize(18);
        tvEmoji.setPadding(dp(2), dp(4), dp(6), dp(4));
        tvEmoji.setOnClickListener(v -> etInput.append(" 🪟 "));
        pill.addView(tvEmoji);

        // Input EditText
        etInput = new EditText(this);
        etInput.setHint("Message (e.g. 48x60 ki 2 window add karo)...");
        etInput.setHintTextColor(0xFF8696A0);
        etInput.setTextColor(getResources().getColor(R.color.wa_text_dark));
        etInput.setTextSize(15);
        etInput.setBackground(null);
        etInput.setPadding(dp(4), dp(8), dp(4), dp(8));
        etInput.setMaxLines(4);
        etInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        pill.addView(etInput);

        // Attach paperclip icon
        btnAttach = new ImageButton(this);
        btnAttach.setImageResource(R.drawable.ic_wa_attach);
        btnAttach.setBackgroundResource(android.R.drawable.list_selector_background);
        btnAttach.setPadding(dp(6), dp(6), dp(6), dp(6));
        btnAttach.setOnClickListener(v -> showAttachmentDialog());
        pill.addView(btnAttach);

        bar.addView(pill);

        // WhatsApp Circular Send / Mic FAB
        btnSend = new ImageButton(this);
        btnSend.setImageResource(R.drawable.ic_wa_mic);
        btnSend.setBackgroundResource(R.drawable.bg_wa_send_fab);
        btnSend.setElevation(dp(3));
        btnSend.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(dp(46), dp(46));
        sendLp.setMarginStart(dp(6));
        btnSend.setLayoutParams(sendLp);
        btnSend.setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            if (!text.isEmpty()) {
                sendMessage(text);
            } else {
                Toast.makeText(this, "Voice: Type your message or choose a quick suggestion!", Toast.LENGTH_SHORT).show();
            }
        });
        bar.addView(btnSend);

        // Switch between Send and Mic icon dynamically
        etInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                boolean hasText = s.toString().trim().length() > 0;
                btnSend.setImageResource(hasText ? R.drawable.ic_wa_send : R.drawable.ic_wa_mic);
            }
        });

        return bar;
    }

    /* ================= 3. QUICK CHIPS CAROUSEL ================= */

    private void populateQuickChips() {
        chipRow.removeAllViews();
        String[][] chips = {
                {"➕ 48×60 Window", "48x60 ki 2 window add karo"},
                {"📐 Sutter & Muliya", "Sutter aur muliya size batao"},
                {"💰 Total Project Cost", "Total cost kitna hua?"},
                {"✂ Pipe Cutting Plan", "Cutting plan dikhao"},
                {"📋 Windows Sheet", "Windows list dikhao"},
                {"👤 Customers", "Saved customer list dikhao"},
                {"🧹 Clear Sheet", "Sab windows clear kardo"},
                {"🛠️ Help & Commands", "help"}
        };

        for (String[] pair : chips) {
            String label = pair[0];
            String prompt = pair[1];

            TextView chip = new TextView(this);
            chip.setText(label);
            chip.setTextSize(13);
            chip.setTextColor(getResources().getColor(R.color.wa_teal));
            chip.setTypeface(null, Typeface.BOLD);
            chip.setBackgroundResource(R.drawable.bg_action_chip);
            chip.setPadding(dp(12), dp(6), dp(12), dp(6));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                etInput.setText(prompt);
                etInput.setSelection(prompt.length());
                sendMessage(prompt);
            });
            chipRow.addView(chip);
        }
    }

    /* ================= 4. DATE & PRIVACY BADGE ================= */

    private void addDateAndPrivacyBadge() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_HORIZONTAL);
        row.setPadding(0, dp(6), 0, dp(6));

        // Date pill
        TextView datePill = new TextView(this);
        datePill.setText("TODAY");
        datePill.setTextSize(11);
        datePill.setTypeface(null, Typeface.BOLD);
        datePill.setTextColor(0xFF54656F);
        datePill.setBackgroundResource(R.drawable.bg_date_badge);
        datePill.setElevation(dp(1));
        row.addView(datePill);

        // On-device privacy banner
        TextView encText = new TextView(this);
        encText.setText("🔒 All calculations & messages are processed 100% on-device (Offline AI)");
        encText.setTextSize(11);
        encText.setTextColor(0xFF667781);
        encText.setPadding(dp(16), dp(4), dp(16), dp(4));
        encText.setGravity(Gravity.CENTER);
        row.addView(encText);

        chatBox.addView(row);
    }

    /* ================= 5. MESSAGE HANDLING ================= */

    private void sendMessage(String text) {
        if (text == null || text.trim().isEmpty()) return;
        text = text.trim();

        addUserMessage(text);
        etInput.setText("");

        // Hide soft keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etInput.getWindowToken(), 0);

        // Show typing indicator
        setTypingStatus(true);
        addTypingIndicator();

        final String userPrompt = text;
        chatBox.postDelayed(() -> {
            removeTypingIndicator();
            setTypingStatus(false);

            OnDeviceTextGenerator.ModelResponse response = engine.process(userPrompt);
            addAgentMessage(response.text, response.actions);
        }, 400 + (long)(Math.random() * 350));
    }

    private void addUserMessage(String text) {
        messages.add(new ChatMessage(ChatMessage.ROLE_USER, text));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        row.setPadding(dp(44), dp(3), dp(4), dp(3));

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackgroundResource(R.drawable.bg_wa_user_bubble);
        bubble.setPadding(dp(12), dp(8), dp(12), dp(6));
        bubble.setElevation(dp(1));

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTextColor(getResources().getColor(R.color.wa_text_dark));
        bubble.addView(tv);

        // Time and Double Blue Tick
        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        metaRow.setPadding(0, dp(3), 0, 0);

        TextView timeTv = new TextView(this);
        timeTv.setText(timeFmt.format(new Date()));
        timeTv.setTextSize(10.5f);
        timeTv.setTextColor(getResources().getColor(R.color.wa_text_muted));
        metaRow.addView(timeTv);

        ImageView tick = new ImageView(this);
        tick.setImageResource(R.drawable.ic_wa_check_double);
        LinearLayout.LayoutParams tickLp = new LinearLayout.LayoutParams(dp(15), dp(15));
        tickLp.setMarginStart(dp(4));
        tick.setLayoutParams(tickLp);
        metaRow.addView(tick);

        bubble.addView(metaRow);
        row.addView(bubble);
        chatBox.addView(row);
        scrollToBottom();
    }

    private void addAgentMessage(String text, List<ChatMessage.Action> actions) {
        messages.add(new ChatMessage(ChatMessage.ROLE_AGENT, text, actions));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.START);
        row.setPadding(dp(4), dp(3), dp(44), dp(3));

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackgroundResource(R.drawable.bg_wa_agent_bubble);
        bubble.setPadding(dp(12), dp(8), dp(12), dp(6));
        bubble.setElevation(dp(1));

        // Assistant tag
        TextView tag = new TextView(this);
        tag.setText("🤖 ALU Assistant");
        tag.setTextSize(10.5f);
        tag.setTypeface(null, Typeface.BOLD);
        tag.setTextColor(getResources().getColor(R.color.wa_teal));
        tag.setPadding(0, 0, 0, dp(3));
        bubble.addView(tag);

        // Body text with Markdown style bolding
        TextView tv = new TextView(this);
        tv.setText(formatMarkdown(text));
        tv.setTextSize(14.5f);
        tv.setTextColor(getResources().getColor(R.color.wa_text_dark));
        tv.setLineSpacing(dp(2.5f), 1f);
        bubble.addView(tv);

        // Action Buttons (Action Chips)
        if (actions != null && !actions.isEmpty()) {
            LinearLayout actionContainer = new LinearLayout(this);
            actionContainer.setOrientation(LinearLayout.HORIZONTAL);
            actionContainer.setPadding(0, dp(8), 0, dp(4));

            HorizontalScrollView actionScroll = new HorizontalScrollView(this);
            actionScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout actionRow = new LinearLayout(this);
            actionRow.setOrientation(LinearLayout.HORIZONTAL);

            for (ChatMessage.Action act : actions) {
                TextView actBtn = new TextView(this);
                actBtn.setText(act.title);
                actBtn.setTextSize(12.5f);
                actBtn.setTypeface(null, Typeface.BOLD);
                actBtn.setTextColor(Color.WHITE);

                GradientDrawable actBg = new GradientDrawable();
                actBg.setShape(GradientDrawable.RECTANGLE);
                actBg.setColor(getResources().getColor(R.color.wa_teal));
                actBg.setCornerRadius(dp(16));
                actBtn.setBackground(actBg);
                actBtn.setPadding(dp(12), dp(6), dp(12), dp(6));

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMarginEnd(dp(8));
                actBtn.setLayoutParams(lp);

                actBtn.setOnClickListener(v -> executeAction(act));
                actionRow.addView(actBtn);
            }
            actionScroll.addView(actionRow);
            actionContainer.addView(actionScroll);
            bubble.addView(actionContainer);
        }

        // Time
        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        metaRow.setPadding(0, dp(3), 0, 0);

        TextView timeTv = new TextView(this);
        timeTv.setText(timeFmt.format(new Date()));
        timeTv.setTextSize(10.5f);
        timeTv.setTextColor(getResources().getColor(R.color.wa_text_muted));
        metaRow.addView(timeTv);

        bubble.addView(metaRow);
        row.addView(bubble);
        chatBox.addView(row);
        scrollToBottom();
    }

    private void addTypingIndicator() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.START);
        row.setPadding(dp(4), dp(3), dp(44), dp(3));
        row.setTag("typing");

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.HORIZONTAL);
        bubble.setBackgroundResource(R.drawable.bg_wa_agent_bubble);
        bubble.setPadding(dp(12), dp(6), dp(12), dp(6));
        bubble.setElevation(dp(1));
        bubble.setGravity(Gravity.CENTER_VERTICAL);

        TextView dots = new TextView(this);
        dots.setText("●  ●  ●");
        dots.setTextSize(14);
        dots.setTextColor(getResources().getColor(R.color.wa_teal));
        bubble.addView(dots);

        TextView text = new TextView(this);
        text.setText(" Thinking...");
        text.setTextSize(12);
        text.setTextColor(getResources().getColor(R.color.wa_text_muted));
        bubble.addView(text);

        row.addView(bubble);
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

    private void setTypingStatus(boolean typing) {
        if (tvStatus != null) {
            tvStatus.setText(typing ? "typing..." : "online • On-Device AI");
            tvStatus.setTextColor(typing ? 0xFF86EFAC : 0xD0FFFFFF);
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
            } else if (title.contains("Clear Chat")) {
                chatBox.removeAllViews();
                addDateAndPrivacyBadge();
                addAgentMessage("Chat clear kar diya gaya hai. Main aapki kya madad kar sakta hoon?", null);
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
}
