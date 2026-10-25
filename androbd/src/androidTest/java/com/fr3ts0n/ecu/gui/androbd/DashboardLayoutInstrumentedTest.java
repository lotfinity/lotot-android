package com.fr3ts0n.ecu.gui.androbd;

import android.os.Handler;
import android.os.Message;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DashboardLayoutInstrumentedTest {
    @Test public void legacyToolbarCannotReappearOverDashboard() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.getPackageName().endsWith(".verification"));
                try {
                    java.lang.reflect.Field field = MainActivity.class.getDeclaredField("mHandler");
                    field.setAccessible(true);
                    Handler handler = (Handler) field.get(activity);
                    // Reproduce the delayed toolbar message that restored the old title bar.
                    if (activity.getActionBar() != null) activity.getActionBar().show();
                    Message message = Message.obtain(handler, MainActivity.MESSAGE_TOOLBAR_VISIBLE, Boolean.TRUE);
                    handler.dispatchMessage(message);
                    assertNotNull(activity.getActionBar());
                    assertFalse(activity.getActionBar().isShowing());
                    java.lang.reflect.Method status = MainActivity.class.getDeclaredMethod("setStatus", CharSequence.class);
                    status.setAccessible(true);
                    status.invoke(activity, "Connected");
                    assertFalse(activity.getActionBar().isShowing());
                } catch (Exception e) { throw new AssertionError(e); }
            });
        }
    }
}
