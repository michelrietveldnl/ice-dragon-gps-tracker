package com.stonedonkey.icedragon;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.util.Date;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

public class LocationService extends Service implements LocationListener {

	private static final String TAG = "IceDragon";
	private static final String LOG_FILE_NAME = "icedragon.csv";

	private int mCheckEvery = 1000 * 60 * 2; // 2 min default
	private int mCheckWindow = 1000 * 15; // 15 second default
	private int mAccruacyThreshold = 0;
	private boolean mSMSEnabled = false;
	private String mSMSNumber = null;
	private int mSMSDelay = 0;
	private String mReportingKey = null;
	private LocationManager mLocationManager;
	private String mSavePath;
	private Handler mCheckHandler;
	private Runnable mRunnableGPS;
	private boolean mIsGPSRunning = false;
	private long mLastGPSCheck = 0;
	private Location mBestLocation = null;
	private float mBatteryLevel;
	private boolean mHTTPEnabled = false;
	private String mHttpEndpoint = null;

	public class LocationServiceBinder extends Binder {
		LocationService getService() {
			return LocationService.this;
		}
	}

	@Override
	public void onCreate() {
		Log.d(TAG, "onCreate() Location Service");

		SetLogSavePath();
		LoadPreferences();

		mCheckHandler = new Handler();
		mRunnableGPS = new Runnable() {

			@Override
			public void run() {
				Log.d(TAG, "Check for turn service on or off");

				long currentTime = System.currentTimeMillis();

				if (!mIsGPSRunning && (mLastGPSCheck == 0 || currentTime - mLastGPSCheck > mCheckEvery)) {
					Log.d(TAG, "Starting Location Check - GO GO GO!!!!");
					StartLocationCheck();
					mIsGPSRunning = true;
					mLastGPSCheck = System.currentTimeMillis();
				}

				if ((mIsGPSRunning && currentTime - mLastGPSCheck > mCheckWindow && mLastGPSCheck != 0) || (mIsGPSRunning && mBestLocation != null && (mBestLocation.getAccuracy() < mAccruacyThreshold && mBestLocation.getAccuracy() > 0.0))) {
					Log.d(TAG, "Ending Location Check - STOP STOP STOP!!!!!");
					endLocationCheck();
					mIsGPSRunning = false;

					// save our best location during the last window
					if (mBestLocation != null) {

						// Get the current battery level
						GetBatteryLevel();

						// only send to webserver and sms if mobile connection
						boolean wifi = hasConnection("WIFI");
						boolean mobile = hasConnection("MOBILE");
						if (mobile)
							SendSMS(mBestLocation);
						if ((mobile || wifi) && mHTTPEnabled && mHttpEndpoint != null)
							WriteHttp(mBestLocation);

						// write to our local log file
						writeLocationToLog(mBestLocation);

						mBestLocation = null;
					}
					mLastGPSCheck = System.currentTimeMillis();
				}

				mCheckHandler.postDelayed(this, 1000);
			}

		};
		mCheckHandler.postDelayed(mRunnableGPS, 0);
	}

	private void LoadPreferences() {
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		mCheckEvery = Integer.parseInt(prefs.getString("PrefCheckEvery", "120")) * 1000;
		mCheckWindow = Integer.parseInt(prefs.getString("PrefCheckLength", "15")) * 1000;
		mAccruacyThreshold = Integer.parseInt(prefs.getString("PrefAccuracyThreshhold", "0"));
		mSMSEnabled = prefs.getBoolean("PrefEnableMMS", false);
		mSMSNumber = prefs.getString("PrefSMSPhone", null);
		mSMSDelay = Integer.parseInt(prefs.getString("PrefSMSDelay", "0")) * 1000;
		mReportingKey = prefs.getString("PrefReportingKey", "");
		mHTTPEnabled = prefs.getBoolean("PrefEnableHTTP", false);
		mHttpEndpoint = prefs.getString("PrefHTTPUrl", null);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(TAG, "onStartCommand() Location Service");
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "onDestroy() Location Service");
		mCheckHandler.removeCallbacks(mRunnableGPS);
		endLocationCheck();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	private void StartLocationCheck() {
		mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

		Criteria crit = new Criteria();
		crit.setAccuracy(Criteria.ACCURACY_FINE);
		String provider = mLocationManager.getBestProvider(crit, true);

		// NOTE : Debugging Code
		// Location startLocation = mLocationManager.getLastKnownLocation(provider);
		// if (startLocation != null)
		// writeLocationToLog(startLocation);

		mLocationManager.requestLocationUpdates(provider, 0, 0, this);
	}

	private void endLocationCheck() {

		mLocationManager.removeUpdates(this);
	}

	@Override
	public void onLocationChanged(Location location) {

		// if we don't have a location this one will do just fine
		if (mBestLocation == null) {
			mBestLocation = location;
			return;
		}

		// if our best location has no accuracy and the new one does.. well that's better
		if (!mBestLocation.hasAccuracy() && location.hasAccuracy()) {
			mBestLocation = location;
			return;
		}

		// if we have two accuracies lets take the best
		if (mBestLocation.getAccuracy() > location.getAccuracy()) {
			mBestLocation = location;
		}

	}

	private void SendSMS(Location bestLocation) {

		if (mSMSEnabled == false || mSMSNumber == null)
			return;

		// if the user sets a sms delay then check if the time has passed
		if (mSMSDelay > 0) {

			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
			long startTime = prefs.getLong("TrackStart", 0);

			if (System.currentTimeMillis() - startTime < mSMSDelay)
				return;
		}

		try {
			SmsManager smsManager = SmsManager.getDefault();
			String msg = "Loc:" + bestLocation.getLatitude() + "," + bestLocation.getLongitude() + " Alt:" + bestLocation.getAltitude() + " Acc:" + bestLocation.getAccuracy() + " Spd:" + bestLocation.getSpeed() + " Bat:" + mBatteryLevel + "%";
			smsManager.sendTextMessage(mSMSNumber, null, msg, null, null);
		}
		catch (Exception ex) {

			Log.d(TAG, "Unable to send SMS");
			ex.printStackTrace();

		}
	}

	@Override
	public void onProviderDisabled(String provider) {
		Toast.makeText(getBaseContext(), "Location Provider Ended", Toast.LENGTH_SHORT).show();
		Log.d(TAG, "Location Provider Is Disabled");

	}

	@Override
	public void onProviderEnabled(String provider) {
		Toast.makeText(getBaseContext(), "Location Provider Started", Toast.LENGTH_SHORT).show();

		Log.d(TAG, "Location Provider Started");

	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		Log.d(TAG, "Status Change " + provider);

	}

	private void SetLogSavePath() {

		mSavePath = "";

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		String startPath = prefs.getString("PrefSavePath", null);
		if (startPath != null)
			mSavePath = startPath;

	}

	private void writeLocationToLog(Location location) {

		String alt = Double.toString(location.getAltitude());
		String lat = Double.toString(location.getLatitude());
		String lon = Double.toString(location.getLongitude());
		String speed = Double.toString(location.getSpeed());
		String bearing = Double.toString(location.getBearing());
		String accuracy = Double.toString(location.getAccuracy());
		//String time = Double.toString(location.getTime());

		String time = DateFormat.getDateTimeInstance().format(new Date());
		
		writeToLogIO("\"" + time + "\"" + "," + alt + "," + lat + "," + lon + "," + speed + "," + bearing + "," + accuracy + "," + mBatteryLevel);

	}

	private void writeToLogIO(String text) {

		try {

			new File(mSavePath).mkdirs();

			// create file to save into
			File file = new File(mSavePath + LOG_FILE_NAME);
			Boolean newFile = file.createNewFile();

			FileOutputStream fOut = new FileOutputStream(file, true);
			OutputStreamWriter osw = new OutputStreamWriter(fOut);

			if (newFile) {
				osw.append("Time,Altitude,Latitude,Longitude,Speed,Bearing,Accuracy,Battery\r\n");
			}

			osw.append(text + "\r\n");

			osw.close();
			fOut.close();

		}
		catch (Exception ex) {
			Toast.makeText(this, "Error writing " + mSavePath + LOG_FILE_NAME, Toast.LENGTH_SHORT).show();
		}
	}

	private void WriteHttp(Location location) {

		String alt = Double.toString(location.getAltitude());
		String lat = Double.toString(location.getLatitude());
		String lon = Double.toString(location.getLongitude());
		String speed = Double.toString(location.getSpeed());
		String bearing = Double.toString(location.getBearing());
		String accuracy = Double.toString(location.getAccuracy());
		String time = Double.toString(location.getTime());

		URL url = null;
		try {
			url = new URL(mHttpEndpoint + "?alt=" + alt + "&lat=" + lat + "&lon=" + lon + "&spd=" + speed + "&bng=" + bearing + "&acc=" + accuracy + "&tme=" + time + "&tid=" + mReportingKey + "&bat=" + mBatteryLevel);
		}
		catch (MalformedURLException e) {
			e.printStackTrace();
		}
		Log.d(TAG, url.toString());

		new UpdateHTTPServerAsyncTask().execute(url);
	}

	private boolean hasConnection(String connectionType) {
		// Ripped from : http://stackoverflow.com/a/7755076/65281
		boolean hasConnection = false;
		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo[] netInfo = cm.getAllNetworkInfo();
		for (NetworkInfo ni : netInfo) {
			if (ni.getTypeName().equalsIgnoreCase(connectionType))
				if (ni.isConnected())
					hasConnection = true;
		}
		return hasConnection;
	}

	private void GetBatteryLevel() {

		Intent bat = this.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		int level = bat.getIntExtra("level", 0);
		int scale = bat.getIntExtra("scale", 100);
		mBatteryLevel = level * 100 / (float) scale;
	}

	private class UpdateHTTPServerAsyncTask extends AsyncTask<URL, Integer, Long> {
		@Override
		protected Long doInBackground(URL... params) {

			try {

				URL url = params[0];

				URLConnection connection = url.openConnection();
				HttpURLConnection httpConnection = (HttpURLConnection) connection;
				httpConnection.connect();
				int response = httpConnection.getResponseCode();
				if (response == 200)
					Log.d(TAG, "Web Call Successful");
				else
					Log.d(TAG, "Web Call Failed");

			}
			catch (Exception ex) {
				Log.e(TAG, "Http Call Failed", ex);
			}
			return null;
		}
	}
}
