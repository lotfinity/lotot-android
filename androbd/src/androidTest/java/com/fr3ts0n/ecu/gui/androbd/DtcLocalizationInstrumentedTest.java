package com.fr3ts0n.ecu.gui.androbd;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.dtcdatabase.DTCDatabase;
import org.junit.Test;
import org.junit.After;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class DtcLocalizationInstrumentedTest {
    private DTCDatabase database() {
        Context context=ApplicationProvider.getApplicationContext();
        assertTrue("Use isolated verification storage",context.getPackageName().endsWith(".verification"));
        return DTCDatabase.getInstance(context);
    }
    @After public void resetLocale() { database().setLocale("en"); }
    @Test public void bundledDefinitionsChangeLanguageWithoutLosingEnglishEvidence() {
        DTCDatabase db=database();
        db.setLocale("en");
        String original=db.getDescription("P0301");
        assertEquals("Cylinder 1 Misfire Detected",original);
        db.setLocale("fr");
        assertEquals("Ratés de combustion détectés sur le cylindre 1",db.getDescription("P0301"));
        assertEquals("fr",db.getDTC("P0301").descriptionLocale);
        db.setLocale("ar-DZ");
        assertEquals("تكتاشفو ratés de combustion في cylindre 1",db.getDescription("P0301"));
        assertEquals("ar-DZ",db.getDTC("P0301").descriptionLocale);
        db.setLocale("en");
        assertEquals(original,db.getDescription("P0301"));
    }
    @Test public void specificDefinitionsAndUntranslatedCodesFallBackSafely() {
        DTCDatabase db=database();
        db.setLocale("en");
        DTCDatabase.DTC original=db.getDTC("P0301","AUDI");
        String rare=db.getDescription("P0001");
        db.setLocale("fr");
        DTCDatabase.DTC specific=db.getDTC("P0301","AUDI");
        assertEquals(original.description,specific.description);
        assertEquals("AUDI",specific.manufacturer);
        assertEquals("en",specific.descriptionLocale);
        assertEquals(rare,db.getDescription("P0001"));
        assertNotNull(rare);
        assertNull(db.getDTC("PFFFF"));
        assertFalse(db.searchByKeyword("fuel").isEmpty());
    }

    @Test public void changingAppLanguageKeepsTheExistingScanEvidence() {
        Context context=ApplicationProvider.getApplicationContext();
        String originalPreference=SettingsActivity.getLanguagePreference(context);
        try (androidx.test.core.app.ActivityScenario<MainActivity> scenario =
                     androidx.test.core.app.ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    java.lang.reflect.Field codes=MainActivity.class.getDeclaredField("mLototScannedDtcs");
                    java.lang.reflect.Field status=MainActivity.class.getDeclaredField("mLototFaultScanStatus");
                    java.lang.reflect.Field time=MainActivity.class.getDeclaredField("mLototFaultScanAt");
                    codes.setAccessible(true);status.setAccessible(true);time.setAccessible(true);
                    codes.set(activity,new org.json.JSONArray().put(new org.json.JSONObject()
                            .put("code","P0301").put("status","confirmed")
                            .put("manufacturer","GENERIC").put("description","Cylinder 1 Misfire Detected")));
                    status.set(activity,"ready");time.setLong(activity,12345L);
                    for (String language : new String[]{"fr","ar-DZ"}) {
                        activity.setLotoTAppLanguage(language);
                        org.json.JSONObject snapshot=new org.json.JSONObject(activity.getLotoTDiagnosticStateJson());
                        assertEquals(12345L,snapshot.getLong("scanned_at"));
                        assertEquals("ready",snapshot.getString("scan_status"));
                        assertEquals(1,snapshot.getJSONArray("dtcs").length());
                        org.json.JSONObject code=snapshot.getJSONArray("dtcs").getJSONObject(0);
                        assertEquals("P0301",code.getString("code"));
                        assertEquals("confirmed",code.getString("status"));
                        assertEquals(language,code.getString("description_language"));
                    }
                } catch (Exception e) { throw new AssertionError(e); }
            });
        } finally { SettingsActivity.setLanguagePreference(context,originalPreference); }
    }

    @Test public void nativeSettingsUseSelectedLanguageAndMirrorNavigation() {
        Context context=ApplicationProvider.getApplicationContext();
        String original=SettingsActivity.getLanguagePreference(context);
        try {
            for (String language : new String[]{"fr","ar-DZ"}) {
                SettingsActivity.setLanguagePreference(context,language);
                try (androidx.test.core.app.ActivityScenario<SettingsActivity> scenario =
                             androidx.test.core.app.ActivityScenario.launch(SettingsActivity.class)) {
                    scenario.onActivity(activity -> {
                        assertEquals(language,SettingsActivity.getResolvedLanguage(activity));
                        android.view.View header=activity.findViewById(R.id.settings_header);
                        assertEquals("ar-DZ".equals(language)?android.view.View.LAYOUT_DIRECTION_RTL:
                                android.view.View.LAYOUT_DIRECTION_LTR,header.getLayoutDirection());
                        assertEquals(android.view.ViewGroup.LayoutParams.WRAP_CONTENT,header.getLayoutParams().height);
                        android.widget.ImageButton back=activity.findViewById(R.id.settings_back);
                        assertTrue(back.getDrawable().isAutoMirrored());
                        if ("fr".equals(language))
                            assertEquals("Ouvrir le dossier des journaux",activity.getString(R.string.open_log_dir));
                    });
                }
            }
        } finally { SettingsActivity.setLanguagePreference(context,original); }
    }
}
