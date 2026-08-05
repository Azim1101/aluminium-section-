package com.digitalalu.alu;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.digitalalu.alu.model.UserProfile;
import com.digitalalu.alu.ui.InsetsHelper;

/** User profile screen — Name, Mobile Number, Address */
public class UserProfileActivity extends AppCompatActivity {

    private EditText etName, etMobile, etAddress;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        UserProfile profile = UserProfile.load(this);

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
        title.setText("MY PROFILE");
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        title.setTypeface(null, Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(title);
        root.addView(top);

        // ----- Scroll Content -----
        ScrollView sc = new ScrollView(this);
        sc.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(20), dp(16), dp(24));

        // ---- Profile Icon Header ----
        LinearLayout headerBox = new LinearLayout(this);
        headerBox.setOrientation(LinearLayout.VERTICAL);
        headerBox.setGravity(Gravity.CENTER);
        headerBox.setBackgroundColor(Color.WHITE);
        headerBox.setPadding(dp(16), dp(28), dp(16), dp(28));
        setRoundedCorners(headerBox);

        TextView iconText = new TextView(this);
        iconText.setText("\uD83D\uDC64");
        iconText.setTextSize(48);
        iconText.setGravity(Gravity.CENTER);
        headerBox.addView(iconText);

        TextView headerTitle = new TextView(this);
        headerTitle.setText("App Owner Details");
        headerTitle.setTextSize(16);
        headerTitle.setTextColor(0xFF152236);
        headerTitle.setTypeface(null, Typeface.BOLD);
        headerTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams httP = new LinearLayout.LayoutParams(-2, -2);
        httP.topMargin = dp(8);
        headerTitle.setLayoutParams(httP);
        headerBox.addView(headerTitle);

        TextView headerSub = new TextView(this);
        headerSub.setText("Aapki details yahan save hongi");
        headerSub.setTextSize(12);
        headerSub.setTextColor(0xFF66758C);
        headerSub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hstP = new LinearLayout.LayoutParams(-2, -2);
        hstP.topMargin = dp(4);
        headerSub.setLayoutParams(hstP);
        headerBox.addView(headerSub);

        LinearLayout.LayoutParams hbP = new LinearLayout.LayoutParams(-1, -2);
        hbP.topMargin = dp(8);
        headerBox.setLayoutParams(hbP);
        content.addView(headerBox);

        // ---- Name Field ----
        content.addView(spacer(20));
        content.addView(makeLabel("FULL NAME"));
        etName = makeEditText("Enter your name", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        etName.setText(profile.name);
        content.addView(etName);

        // ---- Mobile Field ----
        content.addView(spacer(14));
        content.addView(makeLabel("MOBILE NUMBER"));
        etMobile = makeEditText("Enter mobile number", InputType.TYPE_CLASS_PHONE);
        etMobile.setText(profile.mobile);
        content.addView(etMobile);

        // ---- Address Field ----
        content.addView(spacer(14));
        content.addView(makeLabel("ADDRESS"));
        etAddress = new EditText(this);
        etAddress.setHint("Enter full address (city, state, pincode)");
        etAddress.setTextSize(15);
        etAddress.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        etAddress.setMinLines(3);
        etAddress.setGravity(Gravity.TOP | Gravity.START);
        etAddress.setPadding(dp(12), dp(10), dp(12), dp(10));
        etAddress.setBackground(getDrawable(R.drawable.bg_input));
        etAddress.setText(profile.address);
        content.addView(etAddress);

        // ---- Save Button ----
        content.addView(spacer(24));
        Button btnSave = new Button(this);
        btnSave.setText("SAVE PROFILE");
        btnSave.setTextColor(Color.WHITE);
        btnSave.setTextSize(15);
        btnSave.setTypeface(null, Typeface.BOLD);
        btnSave.setBackgroundColor(0xFF2563EB);
        btnSave.setAllCaps(true);
        btnSave.setPadding(dp(16), dp(14), dp(16), dp(14));
        btnSave.setOnClickListener(v -> saveProfile());
        content.addView(btnSave);

        // ---- Info Text ----
        content.addView(spacer(16));
        TextView info = new TextView(this);
        info.setText("\u2139\uFE0F Yeh details app ke agent (ALU Assistant) use karega aapko personalized help dene ke liye. Aapke device pe hi save hoga — kahin share nahi hota.");
        info.setTextSize(11);
        info.setTextColor(0xFF66758C);
        info.setPadding(dp(4), dp(8), dp(4), dp(8));
        content.addView(info);

        sc.addView(content);
        root.addView(sc);
        setContentView(root);
        InsetsHelper.apply(root, top);
    }

    private void saveProfile() {
        String name = etName.getText().toString().trim();
        String mobile = etMobile.getText().toString().trim();
        String address = etAddress.getText().toString().trim();

        if (name.isEmpty()) {
            toast("Please enter your name");
            etName.requestFocus();
            return;
        }

        UserProfile profile = new UserProfile();
        profile.name = name;
        profile.mobile = mobile;
        profile.address = address;
        UserProfile.save(this, profile);

        toast("Profile saved successfully! \u2714");
        finish();
    }

    private TextView makeLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(10);
        tv.setTextColor(0xFF66758C);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.bottomMargin = dp(6);
        tv.setLayoutParams(lp);
        return tv;
    }

    private EditText makeEditText(String hint, int inputType) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setTextSize(15);
        et.setInputType(inputType);
        et.setPadding(dp(12), dp(10), dp(12), dp(10));
        et.setBackground(getDrawable(R.drawable.bg_input));
        et.setMaxLines(1);
        return et;
    }

    private void setRoundedCorners(LinearLayout view) {
        try {
            view.setBackground(getDrawable(R.drawable.bg_card));
        } catch (Exception ignored) {}
    }

    private LinearLayout spacer(int heightDp) {
        LinearLayout sp = new LinearLayout(this);
        sp.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(heightDp)));
        return sp;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
