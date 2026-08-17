/*
 * (C) Copyright 2015 by fr3ts0n <erwin.scheuch-heilig@gmx.at>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston,
 * MA 02111-1307 USA
 *
 */

package com.fr3ts0n.ecu.gui.androbd;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.widget.Toast;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import java.util.Locale;

import com.fr3ts0n.ecu.EcuDataItem;
import com.fr3ts0n.ecu.prot.obd.ElmProt;
import com.fr3ts0n.ecu.prot.obd.ObdProt;

import java.util.HashSet;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SettingsActivity extends AppCompatActivity
	implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback
{
	private TextView settingsTitle;
	private TextView settingsSubtitle;
	private boolean lightSettings;

	// Preference keys referenced from outside this class (MainActivity, ObdItemAdapter) -
	// kept at this level, unlike the Settings-internal-only keys below, so those external
	// call sites don't need to change.
	static final String[] extKeys =
	{
		"ext_file_conversions",
		"ext_file_dataitems"
	};
	static final String KEY_DATA_ITEMS = "data_items";
	static final String KEY_PROT_SELECT = "protocol";
	static final String KEY_COMM_MEDIUM = "comm_medium";
	static final String ELM_MIN_TIMEOUT = "elm_min_timeout";
	static final String ELM_CMD_DISABLE = "elm_cmd_disable";
	static final String KEY_APP_LANGUAGE = "app_language";

	/**
	 * Apply locale based on user preference
	 *
	 * @param activity Activity context
	 */
	static String normalizeLanguageTag(String language)
	{
		String normalized = language == null ? "" : language.trim().replace('_', '-');
		if (normalized.isEmpty()) return "en";
		Locale locale = Locale.forLanguageTag(normalized);
		if (locale == null || locale.getLanguage() == null || locale.getLanguage().isEmpty()) return "en";
		return locale.toLanguageTag();
	}

	static String getLanguagePreference(Context context)
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String language = prefs.getString(KEY_APP_LANGUAGE, "system");
		return language == null || language.trim().isEmpty() ? "system" : language.trim();
	}

	static String setLanguagePreference(Context context, String value)
	{
		String language = value == null ? "system" : value.trim();
		if (language.isEmpty()) language = "system";
		if (!"system".equals(language)) language = normalizeLanguageTag(language);
		PreferenceManager.getDefaultSharedPreferences(context).edit()
				.putString(KEY_APP_LANGUAGE, language).commit();
		return language;
	}

	static String normalizeSupportedLanguage(String language)
	{
		Locale locale = Locale.forLanguageTag(normalizeLanguageTag(language));
		String lang = locale.getLanguage();
		String country = locale.getCountry();
		if ("fr".equalsIgnoreCase(lang)) return "fr";
		if ("en".equalsIgnoreCase(lang)) return "en";
		if ("ar".equalsIgnoreCase(lang) && "DZ".equalsIgnoreCase(country)) return "ar-DZ";
		return "en";
	}

	static String getResolvedLanguage(Context context)
	{
		String language = getLanguagePreference(context);
		if ("system".equals(language))
		{
			Configuration systemConfig = Resources.getSystem().getConfiguration();
			Locale systemLocale;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
				systemLocale = systemConfig.getLocales().isEmpty() ? null : systemConfig.getLocales().get(0);
			else
				systemLocale = systemConfig.locale;
			return systemLocale == null ? "en" : normalizeSupportedLanguage(systemLocale.toLanguageTag());
		}
		return normalizeSupportedLanguage(language);
	}

	static Context wrapLocale(Context context)
	{
		String languageTag = getResolvedLanguage(context);
		Locale locale = Locale.forLanguageTag(languageTag);
		if (locale.getLanguage() == null || locale.getLanguage().isEmpty()) locale = Locale.ENGLISH;
		Configuration config = new Configuration(context.getResources().getConfiguration());
		config.setLocale(locale);
		config.setLayoutDirection(locale);
		return context.createConfigurationContext(config);
	}

	public static void applyLocale(Activity activity)
	{
		String languageTag = getResolvedLanguage(activity);
		Locale locale = Locale.forLanguageTag(languageTag);
		if (locale.getLanguage() == null || locale.getLanguage().isEmpty()) locale = Locale.ENGLISH;
		Locale.setDefault(locale);

		Resources resources = activity.getResources();
		Configuration config = new Configuration(resources.getConfiguration());
		config.setLocale(locale);
		config.setLayoutDirection(locale);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
			activity.createConfigurationContext(config);
		resources.updateConfiguration(config, resources.getDisplayMetrics());
	}

	@Override
	protected void attachBaseContext(Context newBase)
	{
		super.attachBaseContext(wrapLocale(newBase));
	}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		applyLocale(this);
		SharedPreferences appPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		lightSettings = "light".equals(appPrefs.getString("lotot_theme", "dark"));
		setTheme(lightSettings ? R.style.AppTheme_LotoTSettings_Light
				: R.style.AppTheme_LotoTSettings);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_lotot_settings);

		settingsTitle = findViewById(R.id.settings_title);
		settingsSubtitle = findViewById(R.id.settings_subtitle);
		ImageButton back = findViewById(R.id.settings_back);
		back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
		applySettingsSurfaceColors();

		getSupportFragmentManager().addOnBackStackChangedListener(() ->
		{
			if (getSupportFragmentManager().getBackStackEntryCount() == 0)
				setSettingsHeader(getString(R.string.advanced_settings_title),
						getString(R.string.advanced_settings_subtitle));
		});

		if (savedInstanceState == null)
		{
			getSupportFragmentManager()
				.beginTransaction()
				.replace(R.id.settings_content, new SettingsFragment())
				.commit();
		}
	}

	private void applySettingsSurfaceColors()
	{
		int background = ContextCompat.getColor(this, lightSettings
				? R.color.lotot_light_background : R.color.lotot_background);
		int surface = ContextCompat.getColor(this, lightSettings
				? R.color.lotot_light_surface : R.color.lotot_surface);
		int text = ContextCompat.getColor(this, lightSettings
				? R.color.lotot_light_text : R.color.lotot_text);
		int muted = ContextCompat.getColor(this, lightSettings
				? R.color.lotot_light_muted : R.color.lotot_muted);
		findViewById(android.R.id.content).setBackgroundColor(background);
		findViewById(R.id.settings_header).setBackgroundColor(surface);
		findViewById(R.id.settings_content).setBackgroundColor(background);
		settingsTitle.setTextColor(text);
		settingsSubtitle.setTextColor(muted);
	}

	void setSettingsHeader(CharSequence title, CharSequence subtitle)
	{
		if (settingsTitle != null) settingsTitle.setText(title);
		if (settingsSubtitle != null) settingsSubtitle.setText(subtitle);
	}

	/**
	 * Called when the user taps a nested {@code PreferenceScreen} (e.g. "OBD Options",
	 * "CSV Export Options"). AndroidX Preference does not navigate to sub-screens on its
	 * own - the hosting activity has to swap in a new {@link SettingsFragment} rooted at
	 * the tapped screen's key, or the tap silently does nothing.
	 */
	@Override
	public boolean onPreferenceStartScreen(PreferenceFragmentCompat caller, PreferenceScreen pref)
	{
		SettingsFragment fragment = new SettingsFragment();
		Bundle args = new Bundle();
		args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.getKey());
		fragment.setArguments(args);

		setSettingsHeader(pref.getTitle(), getString(R.string.settings_root_subtitle));
		getSupportFragmentManager()
			.beginTransaction()
			.replace(R.id.settings_content, fragment)
			.addToBackStack(pref.getKey())
			.commit();
		return true;
	}

	/**
	 * The actual preference screen - AndroidX preference dialogs/screens are hosted in a
	 * fragment rather than directly in an Activity, unlike the old {@code PreferenceActivity}
	 * API this was originally written against.
	 */
	public static class SettingsFragment
		extends
			PreferenceFragmentCompat
		implements
			SharedPreferences.OnSharedPreferenceChangeListener,
			Preference.OnPreferenceClickListener
	{
		/** The logger object */
		private static final Logger log = Logger.getLogger(SettingsFragment.class.getName());

		/**
		 * app preferences
		 */
		private SharedPreferences prefs;

		/**
		 * key ids for device network settings
		 */
		private static final String[] networkKeys =
		{
			"device_address",
			"device_port"
		};
		/**
		 * key ids for device network settings
		 */
		private static final String[] bluetoothKeys =
		{
			"bt_secure_connection"
		};
		/**
		 * key ids for device network settings
		 */
		private static final String[] usbKeys =
		{
				"comm_baudrate"
		};

		static final String ELM_TIMING_SELECT = "adaptive_timing_mode";
		private static final String KEY_BITCOIN = "bitcoin";
		private static final String KEY_OPEN_LOG_DIR = "open_log_dir";

		Vector<EcuDataItem> items;

		@Override
		public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey)
		{
			prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());

			setPreferencesFromResource(R.xml.settings, rootKey);

			if (requireActivity() instanceof SettingsActivity)
			{
				CharSequence title = rootKey == null
						? getString(R.string.advanced_settings_title)
						: getPreferenceScreen().getTitle();
				CharSequence subtitle = rootKey == null
						? getString(R.string.advanced_settings_subtitle)
						: getString(R.string.settings_root_subtitle);
				((SettingsActivity) requireActivity()).setSettingsHeader(title, subtitle);
			}

			for (String key : extKeys)
			{
				setPrefsText(key);
			}

			// set up communication media selection
			setupCommMediaSelection();
			// set up protocol selection
			setupProtoSelection();
			// set up ELM command selection
			setupElmCmdSelection();
			// set up ELM adaptive timing mode selection
			setupElmTimingSelection();
			// set up selectable PID list
			setupPidSelection();
			// update network selection fields
			updateNetworkSelections();
			setPrefClickListener(KEY_BITCOIN);
			setPrefClickListener(KEY_OPEN_LOG_DIR);
		}

		/**
		 * Each time a nested PreferenceScreen is entered or backed out of, this fragment's
		 * view is destroyed and a new one created (FragmentTransaction.replace()). Something
		 * in the framework/AppCompat already positions list_container correctly below the
		 * status bar and action bar for a freshly created fragment - but that mechanism
		 * doesn't reliably re-run for every (re)created instance, occasionally leaving
		 * list_container at y=0, rendered under the status bar and action bar. Only patch
		 * it when that's actually happened (current position is at/near the top) - the
		 * expected offset is already correct far more often than not, and blindly adding
		 * padding regardless would double it up on top of whatever positioned it correctly
		 * in the first place. Posted rather than applied inline, both so the view has a
		 * real on-screen position to check yet, and so the action bar has its final
		 * measured height.
		 */
		@Override
		public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
		{
			super.onViewCreated(view, savedInstanceState);
			RecyclerView list = getListView();
			list.setClipToPadding(false);
			int side = dp(10);
			list.setPadding(side, dp(12), side, dp(28));
			list.setBackgroundColor(ContextCompat.getColor(requireContext(),
					isLightSettings() ? R.color.lotot_light_background : R.color.lotot_background));
			setDivider(null);
			list.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener()
			{
				@Override public void onChildViewAttachedToWindow(@NonNull View child)
				{
					stylePreferenceRow(child);
				}

				@Override public void onChildViewDetachedFromWindow(@NonNull View child) { }
			});
			view.post(() ->
			{
				for (int i = 0; i < list.getChildCount(); i++)
					stylePreferenceRow(list.getChildAt(i));
			});
		}

		private boolean isLightSettings()
		{
			return requireActivity() instanceof SettingsActivity
					&& ((SettingsActivity) requireActivity()).lightSettings;
		}

		private int dp(int value)
		{
			return Math.round(value * getResources().getDisplayMetrics().density);
		}

		private void stylePreferenceRow(@NonNull View item)
		{
			RecyclerView.LayoutParams params = item.getLayoutParams() instanceof RecyclerView.LayoutParams
					? (RecyclerView.LayoutParams) item.getLayoutParams()
					: new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT,
							RecyclerView.LayoutParams.WRAP_CONTENT);
			TextView title = item.findViewById(android.R.id.title);
			TextView summary = item.findViewById(android.R.id.summary);
			ImageView icon = item.findViewById(android.R.id.icon);

			int text = ContextCompat.getColor(requireContext(), isLightSettings()
					? R.color.lotot_light_text : R.color.lotot_text);
			int muted = ContextCompat.getColor(requireContext(), isLightSettings()
					? R.color.lotot_light_muted : R.color.lotot_muted);
			int accent = ContextCompat.getColor(requireContext(), isLightSettings()
					? R.color.lotot_light_accent : R.color.lotot_accent);
			int surface = ContextCompat.getColor(requireContext(), isLightSettings()
					? R.color.lotot_light_surface : R.color.lotot_surface);
			int line = ContextCompat.getColor(requireContext(), isLightSettings()
					? R.color.lotot_light_divider : R.color.lotot_divider);

			GradientDrawable card = new GradientDrawable();
			card.setColor(surface);
			card.setCornerRadius(dp(16));
			card.setStroke(dp(1), line);
			ColorStateList rippleColor = ColorStateList.valueOf(
					android.graphics.Color.argb(32, 202, 255, 0));
			item.setBackground(new RippleDrawable(rippleColor, card, null));
			params.setMargins(dp(6), dp(4), dp(6), dp(4));
			item.setMinimumHeight(dp(68));
			item.setLayoutParams(params);
			if (title != null) title.setTextColor(text);
			if (summary != null) summary.setTextColor(muted);
			if (icon != null) icon.setColorFilter(accent);
		}

		/**
		 * Set this fragment as the click listener for a preference, if it exists in the
		 * currently displayed (sub-)screen. findPreference() only searches the tree rooted
		 * at whichever screen setPreferencesFromResource() was given - a key that lives in
		 * a different top-level PreferenceScreen won't be found, which is expected rather
		 * than an error.
		 */
		private void setPrefClickListener(String key)
		{
			Preference pref = findPreference(key);
			if (pref != null)
			{
				pref.setOnPreferenceClickListener(this);
			}
		}

		@Override
		public void onResume()
		{
			super.onResume();
			// add handler for selection update
			prefs.registerOnSharedPreferenceChangeListener(this);
		}

		@Override
		public void onPause()
		{
			prefs.unregisterOnSharedPreferenceChangeListener(this);
			super.onPause();
		}

		@Override
		public void onDisplayPreferenceDialog(@NonNull Preference preference)
		{
			if (preference instanceof SearchableMultiSelectListPreference)
			{
				SearchableMultiSelectListPreferenceDialogFragment fragment =
					SearchableMultiSelectListPreferenceDialogFragment.newInstance(preference.getKey());
				fragment.setTargetFragment(this, 0);
				fragment.show(getParentFragmentManager(), "androidx.preference.PreferenceFragment.DIALOG");
				return;
			}
			super.onDisplayPreferenceDialog(preference);
		}

		/**
		 * set up protocol selection
		 */
		void setupProtoSelection()
		{
			ListPreference pref = (ListPreference) findPreference(KEY_PROT_SELECT);
			if (pref == null) return;
			ElmProt.PROT[] values = ElmProt.PROT.values();
			CharSequence[] titles = new CharSequence[values.length];
			CharSequence[] keys = new CharSequence[values.length];
			int i = 0;
			for (ElmProt.PROT proto : values)
			{
				titles[i] = proto.toString();
				keys[i] = String.valueOf(proto.ordinal());
				i++;
			}
			// set enries and keys
			pref.setEntries(titles);
			pref.setEntryValues(keys);
			pref.setDefaultValue(String.valueOf(CommService.MEDIUM.BLUETOOTH.ordinal()));
			// show current selection
			pref.setSummary(pref.getEntry());
		}

		/**
		 * set up protocol selection
		 */
		void setupElmTimingSelection()
		{
			ListPreference pref = (ListPreference) findPreference(ELM_TIMING_SELECT);
			if (pref == null) return;
			ElmProt.AdaptTimingMode[] values = ElmProt.AdaptTimingMode.values();
			CharSequence[] titles = new CharSequence[values.length];
			CharSequence[] keys = new CharSequence[values.length];
			int i = 0;
			for (ElmProt.AdaptTimingMode mode : values)
			{
				titles[i] = mode.toString();
				keys[i] = mode.toString();
				i++;
			}
			// set entries and keys
			pref.setEntries(titles);
			pref.setEntryValues(keys);
			pref.setDefaultValue(titles[0]);
			// show current selection
			pref.setSummary(pref.getEntry());
		}

		/**
		 * set up protocol selection
		 */
		void setupElmCmdSelection()
		{
			MultiSelectListPreference pref =
				(MultiSelectListPreference) findPreference(ELM_CMD_DISABLE);
			if (pref == null) return;
			ElmProt.CMD[] values = ElmProt.CMD.values();
			HashSet<String> selections = new HashSet<>();
			CharSequence[] titles = new CharSequence[values.length];
			CharSequence[] keys = new CharSequence[values.length];
			int i = 0;
			for (ElmProt.CMD cmd : values)
			{
				titles[i] = cmd.toString();
				keys[i] = cmd.toString();
				if(!cmd.isEnabled()) selections.add(cmd.toString());
				i++;
			}
			// set enries and keys
			pref.setEntries(titles);
			pref.setEntryValues(keys);
			pref.setValues(selections);
		}

		/**
		 * set up protocol selection
		 */
		void setupCommMediaSelection()
		{
			ListPreference pref = (ListPreference) findPreference(KEY_COMM_MEDIUM);
			if (pref == null) return;
			CommService.MEDIUM[] values = CommService.MEDIUM.values();
			CharSequence[] titles = new CharSequence[values.length];
			CharSequence[] keys = new CharSequence[values.length];
			int i = 0;
			for (CommService.MEDIUM proto : values)
			{
				titles[i] = proto.toString();
				keys[i] = String.valueOf(proto.ordinal());
				i++;
			}
			// set enries and keys
			pref.setEntries(titles);
			pref.setEntryValues(keys);
			pref.setDefaultValue(titles[0]);
			// show current selection
			pref.setSummary(pref.getEntry());
		}

		/**
		 * set up selection for PIDs
		 */
		void setupPidSelection()
		{
			SearchableMultiSelectListPreference itemList =
				(SearchableMultiSelectListPreference) findPreference(KEY_DATA_ITEMS);
			if (itemList == null) return;

			// collect data items for selection
			items = ObdProt.dataItems.getSvcDataItems(ObdProt.OBD_SVC_DATA);
			HashSet<String> selections = new HashSet<>();
			CharSequence[] titles = new CharSequence[items.size()];
			CharSequence[] keys = new CharSequence[items.size()];
			// loop through data items
			int i = 0;
			for (EcuDataItem currItem : items)
			{
				titles[i] = currItem.label;
				keys[i] = currItem.toString();
				selections.add(currItem.toString());
				i++;
			}
			// set enries and keys
			itemList.setEntries(titles);
			itemList.setEntryValues(keys);

			// if there is no item selected, mark all as selected
			if (itemList.getValues().size() == 0)
			{
				itemList.setValues(selections);
			}
		}

		/**
		 * set up preference text for extension files
		 *
		 * @param key preference key to be set up
		 */
		void setPrefsText(String key)
		{
			Preference prefComp = findPreference(key);
			if (prefComp == null) return;
			prefComp.setOnPreferenceClickListener(this);
			String value = prefs.getString(key, null);
			if (value != null)
			{
				prefComp.setSummary(value);
			}
		}

		/**
		 * Update fields for network parameters
		 *
		 * enable/disable elements for network parameters
		 * based on selection of communication medium
		 */
		void updateNetworkSelections()
		{
			boolean networkSelected =
				String.valueOf(CommService.MEDIUM.NETWORK.ordinal())
					.equals(prefs.getString(KEY_COMM_MEDIUM,""));
			boolean bluetoothSelected =
				String.valueOf(CommService.MEDIUM.BLUETOOTH.ordinal())
					.equals(prefs.getString(KEY_COMM_MEDIUM,""));
			boolean usbSelected =
					String.valueOf(CommService.MEDIUM.USB.ordinal())
							.equals(prefs.getString(KEY_COMM_MEDIUM,""));

			// enable/disable network specific entries
			for(String key : networkKeys)
			{
				Preference pref = findPreference(key);
				if (pref != null) pref.setEnabled(networkSelected);
			}

			// enable/disable bluetooth specific entries
			for(String key : bluetoothKeys)
			{
				Preference pref = findPreference(key);
				if (pref != null) pref.setEnabled(bluetoothSelected);
			}

			// enable/disable usb specific entries
			for(String key : usbKeys)
			{
				Preference pref = findPreference(key);
				if (pref != null) pref.setEnabled(usbSelected);
			}
		}

		private void openLogDirectory()
		{
			java.io.File logDir = new java.io.File(
					FileHelper.getPath(requireContext()), "log");
			if (!logDir.isDirectory())
			{
				Toast.makeText(requireContext(),
						R.string.log_dir_not_found,
						Toast.LENGTH_LONG).show();
				return;
			}
			try
			{
				Intent intent = new Intent(Intent.ACTION_VIEW);
				intent.setDataAndType(Uri.fromFile(logDir), "resource/folder");
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(Intent.createChooser(intent,
						getString(R.string.open_log_dir)));
			}
			catch (Exception e)
			{
				// No file manager installed, or Android 7+ blocked file:// URI;
				// copy the path to clipboard as a fallback.
				ClipboardManager cm = (ClipboardManager)
						requireContext().getSystemService(CLIPBOARD_SERVICE);
				cm.setPrimaryClip(ClipData.newPlainText(
						"log_dir", logDir.getAbsolutePath()));
				Toast.makeText(requireContext(),
						R.string.log_dir_no_app,
						Toast.LENGTH_LONG).show();
			}
		}

		@Override
		public boolean onPreferenceClick(Preference preference)
		{
			Intent intent = preference.getIntent();
			try
			{
				if(KEY_BITCOIN.equals(preference.getKey()))
				{
					// special handling for bitcoin VIEW intent
					startActivity(intent);
				}
				else if(KEY_OPEN_LOG_DIR.equals(preference.getKey()))
				{
					openLogDirectory();
					return true;
				}
				else
				{
					// OPEN intents require result handling
					intent.addCategory(Intent.CATEGORY_OPENABLE);
					startActivityForResult(intent, preference.hashCode());
				}
			}
			catch(Exception e)
			{
				log.log(Level.SEVERE, "Settings", e);
				Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
			}
			return true;
		}

		/**
		 * Handler for result messages from other activities
		 */
		@Override
		public void onActivityResult(int requestCode, int resultCode, Intent data)
		{
			Preference pref;
			SharedPreferences.Editor ed = prefs.edit();
			String value = (resultCode == Activity.RESULT_OK) ? String.valueOf(data.getData()) : null;
			// find the right key
			for (String key : extKeys)
			{
				pref = findPreference(key);
				if (pref.hashCode() == requestCode)
				{
					ed.putString(key, value);
					pref.setSummary(value != null ? value : getString(R.string.select_extension));
				}
			}

			ed.apply();
		}

		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
		{
			Preference pref = findPreference(key);

			if (pref instanceof ListPreference)
			{
				ListPreference currPref = (ListPreference) pref;
				currPref.setSummary(currPref.getEntry());
			}
			else
			if (pref instanceof EditTextPreference)
			{
				EditTextPreference currPref = (EditTextPreference) pref;
				currPref.setSummary(currPref.getText());
			}

			if(KEY_COMM_MEDIUM.equals(key))
				updateNetworkSelections();

			if(ELM_TIMING_SELECT.equals(key))
				//noinspection ConstantConditions
				findPreference(ELM_MIN_TIMEOUT)
					.setEnabled(ElmProt.AdaptTimingMode.SOFTWARE.toString()
						          .equals(((ListPreference)pref).getValue())
					           );

			if(KEY_APP_LANGUAGE.equals(key))
			{
				SettingsActivity.applyLocale(requireActivity());
				requireActivity().recreate();
			}
		}
	}
}
