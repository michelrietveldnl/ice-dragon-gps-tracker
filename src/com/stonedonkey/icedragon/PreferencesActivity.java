package com.stonedonkey.icedragon;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.widget.Toast;

public class PreferencesActivity extends PreferenceActivity {

	private static int FILE_BROWSER_RESULT = 0;
	private SharedPreferences prefs;

	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.settings);
		prefs = PreferenceManager.getDefaultSharedPreferences(this);

	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {

		if (preference.getKey().equalsIgnoreCase("PrefFileSavePath")) {
			Intent i = new Intent();
			i.setClass(this, FileBrowserActivity.class);

			String startPath = prefs.getString("PrefSavePath", null);
			if (startPath != null) {
				i.putExtra("StartPath", startPath);
			}

			startActivityForResult(i, FILE_BROWSER_RESULT);
		}
		else if (preference.getKey().equalsIgnoreCase("manual")) {
			
			 Intent intent = new Intent(Intent.ACTION_VIEW);
		      intent.setData(Uri.parse("http://blog.stonedonkey.com/wp-content/uploads/2013/05/IceDragonGPSTracker.pdf"));
		      startActivity(intent);
		      return true;
		}
		
		else if (preference.getKey().equalsIgnoreCase("project")) {
			
			 Intent intent = new Intent(Intent.ACTION_VIEW);
		      intent.setData(Uri.parse("http://blog.stonedonkey.com/category/operation-ice-dragon/"));
		      startActivity(intent);
		      return true;
		}
		
		return super.onPreferenceTreeClick(preferenceScreen, preference);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		if (resultCode == RESULT_OK) {
			String path = data.getStringExtra("path");
			Toast.makeText(this, "log file will save to " + path, Toast.LENGTH_SHORT).show();
			prefs.edit().putString("PrefSavePath", path).commit();
		}

		super.onActivityResult(requestCode, resultCode, data);
	}

}
