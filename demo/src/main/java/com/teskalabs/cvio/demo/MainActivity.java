package com.teskalabs.cvio.demo;

import android.*;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.teskalabs.cvio.CatVision;
import com.teskalabs.seacat.android.client.SeaCatClient;

import java.io.IOException;

public class MainActivity extends AppCompatActivity implements StoppedFragment.OnFragmentInteractionListener, StartedFragment.OnFragmentInteractionListener {

	private BroadcastReceiver receiver;
	private FirebaseAnalytics mFirebaseAnalytics;
	private CatVision catvision;
	// Requests
	private int CATVISION_REQUEST_CODE = 100;
	private int API_KEY_OBTAINER_REQUEST = 101;
	private static final String TAG = MainActivity.class.getName();
	// Preferences
	public static String SAVED_API_KEY_ID = "SAVED_API_KEY_ID";


	// Activity Lifecycle methods ------------------------------------------------------------------

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		catvision = CatVision.getInstance(this);
		catvision.setCustomId(CatVision.DEFAULT_CUSTOM_ID);

		if (findViewById(R.id.fragment_container) != null)
		{
			if (savedInstanceState == null)
			{
				Fragment firstFragment = new StoppedFragment();

				firstFragment.setArguments(getIntent().getExtras());

				getSupportFragmentManager().beginTransaction()
					.add(R.id.fragment_container, firstFragment, StoppedFragment.class.toString())
					.commit();
			}
		}

		// Obtain the FirebaseAnalytics instance.
		mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
			if (intent.hasCategory(SeaCatClient.CATEGORY_SEACAT)) {
				String action = intent.getAction();
				if (action.equals(SeaCatClient.ACTION_SEACAT_CLIENTID_CHANGED)) {
//					clientTagTextView.setText(catvision.getClientTag());
					return;
				} else if (action.equals(SeaCatClient.ACTION_SEACAT_STATE_CHANGED)) {
//					statusTextView.setText(SeaCatClient.getState());
					return;
				}
			}
			else if (intent.hasCategory(CatVision.CATEGORY_CVIO)) {
				String action = intent.getAction();
				if (action.equals(CatVision.ACTION_CVIO_SHARE_STARTED)) {

					Fragment newFragment = new StartedFragment();
					FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
					ft.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left);
					ft.replace(R.id.fragment_container, newFragment, StartedFragment.class.toString());
					ft.commit();

					return;
				} else if (action.equals(CatVision.ACTION_CVIO_SHARE_STOPPED)) {
					Fragment newFragment = new StoppedFragment();

					FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
					ft.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right);
					ft.replace(R.id.fragment_container, newFragment, StoppedFragment.class.toString());
					ft.commit();

					return;
				}
			}
			}
		};

		// Deep linking
		Uri data = this.getIntent().getData();
		if (data != null && data.isHierarchical()) {
			// Setting the API key
			String apikey = data.getQueryParameter("apikey").replace(" ", "+");
			setApiKeyId(apikey);
			// Showing a dialog
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(getResources().getString(R.string.app_name));
			builder.setMessage(getResources().getString(R.string.dl_dialog_message));
			builder.setCancelable(true);
			builder.setPositiveButton(
					getResources().getString(R.string.dialog_button_ok),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							dialog.cancel();
						}
					});
			AlertDialog alert = builder.create();
			alert.show();
		}
	}

	@Override
	protected void onStart() {
		super.onStart();

		IntentFilter intentFilter;

		intentFilter = new IntentFilter();
		intentFilter.addCategory(CatVision.CATEGORY_CVIO);
		intentFilter.addAction(CatVision.ACTION_CVIO_SHARE_STARTED);
		intentFilter.addAction(CatVision.ACTION_CVIO_SHARE_STOPPED);
		registerReceiver(receiver, intentFilter);

		intentFilter = new IntentFilter();
		intentFilter.addCategory(SeaCatClient.CATEGORY_SEACAT);
		intentFilter.addAction(SeaCatClient.ACTION_SEACAT_STATE_CHANGED);
		intentFilter.addAction(SeaCatClient.ACTION_SEACAT_CSR_NEEDED);
		intentFilter.addAction(SeaCatClient.ACTION_SEACAT_CLIENTID_CHANGED);
		registerReceiver(receiver, intentFilter);
	}

	@Override
	protected void onStop() {
		super.onStop();
		unregisterReceiver(receiver);
	}


	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == CATVISION_REQUEST_CODE) {
			catvision.onActivityResult(this, resultCode, data);

			Bundle bundle = new Bundle();
			bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "start_capture");
			bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "menu_item");
			mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);

		} else if (requestCode == API_KEY_OBTAINER_REQUEST) {
			if (resultCode == RESULT_OK) {
				// Setting a new API key from the scan
				String apikey_id = data.getStringExtra("apikey_id");
				if (apikey_id != null) {
					// Setting the API key
					setApiKeyId(apikey_id);
					// Showing a dialog
					AlertDialog.Builder builder = new AlertDialog.Builder(this);
					builder.setTitle(getResources().getString(R.string.app_name));
					builder.setMessage(getResources().getString(R.string.qr_dialog_message));
					builder.setCancelable(true);
					builder.setPositiveButton(
							getResources().getString(R.string.dialog_button_ok),
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int id) {
									dialog.cancel();
								}
							});
					AlertDialog alert = builder.create();
					alert.show();
				}
			}
		}
	}

	private void shareTextUrl() {
		Intent share = new Intent(android.content.Intent.ACTION_SEND);
		share.setType("text/plain");
		share.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);

		// Add data to the intent, the receiving app will decide what to do with it.
		share.putExtra(Intent.EXTRA_SUBJECT, "CatVision.io - Remote Access Link");
		share.putExtra(Intent.EXTRA_TEXT, "Please use this link to access my screen remotely. https://www.catvision.io");

		startActivity(Intent.createChooser(share, "Choose where to send a remote access link."));
	}


	// Menu ----------------------------------------------------------------------------------------

	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu_main, menu);
		return true;
	}

	public boolean onMenuItemClickShowClientTag(MenuItem v) {
		// Getting the client tag
		String clientTag = catvision.getClientTag();
		// Starting the InfoActivity
		Intent intent = new Intent(getApplicationContext(), InfoActivity.class);
		intent.putExtra("client_tag", "Client tag: "+ clientTag);
		startActivity(intent);
		return true;
	}

	public boolean onMenuItemClickResetIdentity(MenuItem v) {
		try {
			SeaCatClient.reset();
			// Save also in this context
			savePreferenceString(SAVED_API_KEY_ID, null);
			refreshFragments();
			// Toast
			Toast.makeText(this, "Client identity reset.", Toast.LENGTH_LONG).show();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return true;
	}

	public boolean onMenuItemClickTestArea(MenuItem v) {
		Intent intent = new Intent(getApplicationContext(), TestAreaActivity.class);
		startActivity(intent);
		return true;
	}

	public boolean onMenuItemClickOverrideApiKeyId(MenuItem v) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Enter new Api Key id");

		// Set up the input
		final EditText input = new EditText(this);

		// Specify the type of input expected
		input.setInputType(InputType.TYPE_CLASS_TEXT);
		builder.setView(input);

		// Set up the buttons
		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				String ApiKeyId = input.getText().toString();
				CatVision.resetWithAPIKeyId(MainActivity.this, ApiKeyId);
			}
		});
		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();
			}
		});
		builder.show();
		return true;
	}


	// ---------------------------------------------------------------------------------------------
	public void onClickStartSharing(View v) {
		String api_key = getPreferenceString(SAVED_API_KEY_ID);
		if (api_key != null) {
			catvision.requestStart(this, CATVISION_REQUEST_CODE);
		} else {
			startQRScanActivity();
		}
	}

	public void onClickStopSharing(View v) {
		catvision.stop();
	}

	public void onClickSendLink(View v) {
		shareTextUrl();
	}

	// Fragment callbacks --------------------------------------------------------------------------

	@Override
	public void onFragmentInteractionStartRequest() {
	}

	@Override
	public void onFragmentInteractionStopRequest() {
	}

	// Custom methods ------------------------------------------------------------------------------
	/**
	 * Sets the API key to CatVision.
	 * @param apiKeyId String
	 */
	public void setApiKeyId(String apiKeyId) {
		CatVision.resetWithAPIKeyId(MainActivity.this, apiKeyId);
		// Save also in this context
		savePreferenceString(SAVED_API_KEY_ID, apiKeyId);
		refreshFragments();
	}

	/**
	 * Refreshes fragments when some important value changes.
	 */
	public void refreshFragments() {
		// Refresh fragments
		StoppedFragment fragmentStopped = (StoppedFragment)getSupportFragmentManager().findFragmentByTag(StoppedFragment.class.toString());
		if (fragmentStopped != null) {
			fragmentStopped.refreshApiKeyRelatedView(null);
		}
	}

	/**
	 * Starts an activity that retrieves the Api Key ID.
	 */
	public void startQRScanActivity() {
		Intent intent = new Intent(getApplicationContext(), ApiKeyObtainerActivity.class);
		startActivityForResult(intent, API_KEY_OBTAINER_REQUEST);
	}

	/**
	 * Getting a string from shared preferences.
	 * @param name String
	 * @return String
	 */
	public String getPreferenceString(String name) {
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		return settings.getString(name, null);
	}

	/**
	 * Setting a string to shared preferences.
	 * @param name String
	 * @param value String
	 */
	public void savePreferenceString(String name, String value) {
		SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
		editor.putString(name, value);
		editor.apply();
	}

	@Override
	public boolean onSupportNavigateUp() {
		onBackPressed();
		return true;
	}
}
