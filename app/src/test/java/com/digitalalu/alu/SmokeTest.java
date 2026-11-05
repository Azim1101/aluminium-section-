package com.digitalalu.alu;

import static org.junit.Assert.assertNotNull;

import android.app.Activity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Crash smoke test — har screen ko asli Android framework (Robolectric) par
 * kholta hai. Koi bhi screen tooti hai to build fail hota hai (galat APK
 * phone tak nahi jaata).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SmokeTest {

    private <T extends Activity> void open(Class<T> cls) {
        ActivityController<T> c = Robolectric.buildActivity(cls).setup();
        assertNotNull(c.get());
        c.pause().stop().destroy();
    }

    @Test public void mainActivity_opens() { open(MainActivity.class); }

    @Test public void mainActivity_allTabsAndRowAdd() {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = c.get();
        androidx.viewpager2.widget.ViewPager2 pager = a.findViewById(R.id.pager);
        assertNotNull(pager);
        assertNotNull(a.findViewById(R.id.bottomBar));
        a.findViewById(R.id.navEstimate).performClick();
        a.findViewById(R.id.navCutting).performClick();
        a.findViewById(R.id.navWindows).performClick();
        a.findViewById(R.id.btnAddRow).performClick();
        c.pause().stop().destroy();
    }

    @Test public void agentActivity_opens() { open(AgentActivity.class); }


    @Test public void priceActivity_opens() { open(PriceActivity.class); }

    @Test public void userProfileActivity_opens() { open(UserProfileActivity.class); }

    @Test public void manualCuttingActivity_opens() { open(ManualCuttingActivity.class); }

    @Test public void customerActivity_opens() { open(CustomerActivity.class); }
}
