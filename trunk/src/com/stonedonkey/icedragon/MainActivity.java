package com.stonedonkey.icedragon;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements OnClickListener {

	public static final String TAG = "IceDragon";
	private static final int SERVICE_RUNNING = 1;
	private static final int SERVICE_DISABLED = 2;
	private int serviceStatus = SERVICE_DISABLED;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// set listeners for buttons
		Button start = (Button) findViewById(R.id.ButtonStartService);
		Button end = (Button) findViewById(R.id.ButtonEndService);
		TextView httpEndPoint = (TextView) findViewById(R.id.TextViewHttpEndpoint);
		TextView logPath = (TextView) findViewById(R.id.TextViewLogPath);
		start.setOnClickListener(this);
		end.setOnClickListener(this);
		httpEndPoint.setOnClickListener(this);
		logPath.setOnClickListener(this);

		GetSettings();

	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus)
			GetSettings();
	}

	private void GetSettings() {
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		
		final TextView checkEvery = (TextView)findViewById(R.id.TextViewCheckEvery);
		final TextView checkLength = (TextView)findViewById(R.id.TextViewCheckLength);
		final TextView accuracyThresh = (TextView)findViewById(R.id.TextViewAccuracyThreshold);
		final Boolean bMMS = prefs.getBoolean("PrefEnableMMS", false);
		final TextView smsEnabled = (TextView) findViewById(R.id.TextViewSMSEnabled);
		final TextView smsNumber = (TextView)findViewById(R.id.TextViewSMSNumber);
		final TextView smsDelay = (TextView) findViewById(R.id.TextViewSMSDelay);
		final TextView trackName = (TextView)findViewById(R.id.TextViewTrackName);
		final TextView trackLogPath = (TextView)findViewById(R.id.TextViewLogPath);
		final TextView httpEnabled = (TextView)findViewById(R.id.TextViewHttpEnabled);
		final TextView httpEndPoint = (TextView)findViewById(R.id.TextViewHttpEndpoint);
		
		checkEvery.setText(prefs.getString("PrefCheckEvery", "120") + " seconds");
		checkLength.setText(prefs.getString("PrefCheckLength", "15") + " seconds");
		accuracyThresh.setText(prefs.getString("PrefAccuracyThreshhold", "0") + " meters");
		
		String disabledText = "";
		if (bMMS)
			smsEnabled.setText("Yes");
		else {
			smsEnabled.setText("No");
			disabledText = " (disabled)";
		}

		smsNumber.setText(prefs.getString("PrefSMSPhone", "Not Set") + disabledText);
		smsDelay.setText(prefs.getString("PrefSMSDelay", "0") + " seconds" + disabledText);

		trackName.setText(prefs.getString("PrefReportingKey", "Not Set"));
		trackLogPath.setText(prefs.getString("PrefSavePath", "Not Set"));

		final Boolean bHTTP = prefs.getBoolean("PrefEnableHTTP", false);
		if (bHTTP) {
			httpEnabled.setText("Yes");
			httpEndPoint.setText(prefs.getString("PrefHTTPUrl", "Not Set"));
		}
		else {
			httpEnabled.setText("No");
			httpEndPoint.setText("Disabled");
			disabledText = " (disabled)";
		}

	}
	
	@Override
	public void onClick(View v) {
		switch (v.getId())
		{
		case R.id.ButtonStartService:

			// set our start time for this track
			final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
			prefs.edit().putLong("TrackStart", System.currentTimeMillis()).commit();
			
			SetServiceStatusInPreferences(true);
			
			startService(new Intent(this, LocationService.class));
			v.setEnabled(true);
			SetButtonsEnabled();
			Toast.makeText(this, "Service Starting", Toast.LENGTH_SHORT).show();
			break;

		case R.id.ButtonEndService:
			stopService(new Intent(this, LocationService.class));
			SetServiceStatusInPreferences(false);
			SetButtonsEnabled();
			Toast.makeText(this, "Service Ending", Toast.LENGTH_SHORT).show();
			break;

		case R.id.TextViewHttpEndpoint:
		case R.id.TextViewLogPath:
			TextView httpEndPoint = (TextView) v;
			Toast.makeText(this, httpEndPoint.getText(), Toast.LENGTH_SHORT).show();
			break;

		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		SetButtonsEnabled();
	}

	private void SetButtonsEnabled() {

		Button start = (Button) findViewById(R.id.ButtonStartService);
		Button end = (Button) findViewById(R.id.ButtonEndService);

		if (isServiceRunning())
			serviceStatus = SERVICE_RUNNING;
		else
			serviceStatus = SERVICE_DISABLED;

		switch (serviceStatus)
		{
		case SERVICE_RUNNING:
			start.setEnabled(false);
			end.setEnabled(true);
			break;
		case SERVICE_DISABLED:
			start.setEnabled(true);
			end.setEnabled(false);
		}
	}
	private void SetServiceStatusInPreferences(Boolean state)
	{
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);	
		prefs.edit().putBoolean("PrefIsServiceRunning",state ).commit();
		
	}
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {

		switch (item.getItemId())
		{
		case R.id.menu_settings:
			Intent intent = new Intent();
			intent.setClass(this, PreferencesActivity.class);
			startActivity(intent);
			break;
		}

		return super.onMenuItemSelected(featureId, item);

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	private boolean isServiceRunning() {
		final ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
			if ("com.stonedonkey.icedragon.LocationService".equals(service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}
}
