package com.stonedonkey.icedragon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class ReceiverStartup extends BroadcastReceiver {

	private static final String TAG = "IceDragon";

	@Override
	public void onReceive(Context context, Intent intent) {

		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		Boolean isServiceLastRunning = prefs.getBoolean("PrefIsServiceRunning", false);
		Boolean allowRestartofService = prefs.getBoolean("PrefStartServiceOnReboot", true);

		Log.d(TAG,"Service last running = " + isServiceLastRunning);
		Log.d(TAG,"Allow Service Restart = " + allowRestartofService);
		
		if (isServiceLastRunning && allowRestartofService) {
			Log.d(TAG, "Service started via BroadcastReceiver");
			Intent service = new Intent(context, LocationService.class);
			context.startService(service);
		}
		else {
			Log.d(TAG, "Service NOT started via BroadcastReceiver");
		}

	}

}
