package com.ahbrendan.beaty;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.ahbrendan.beaty.util.SystemUiHider;

import android.annotation.TargetApi;
import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.*;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 * 
 * @see SystemUiHider
 */
public class FullscreenActivity extends Activity {
	/**
	 * Whether or not the system UI should be auto-hidden after
	 * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
	 */
	private static final boolean AUTO_HIDE = true;

	/**
	 * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
	 * user interaction before hiding the system UI.
	 */
	private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

	/**
	 * If set, will toggle the system UI visibility upon interaction. Otherwise,
	 * will show the system UI visibility upon interaction.
	 */
	private static final boolean TOGGLE_ON_CLICK = true;

	/**
	 * The flags to pass to {@link SystemUiHider#getInstance}.
	 */
	private static final int HIDER_FLAGS = SystemUiHider.FLAG_HIDE_NAVIGATION;

	/**
	 * The instance of the {@link SystemUiHider} for this activity.
	 */
	private SystemUiHider mSystemUiHider;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
		StrictMode.setThreadPolicy(policy);
		
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_fullscreen);

		final View controlsView = findViewById(R.id.fullscreen_content_controls);
		final TextView contentView = (TextView)findViewById(R.id.fullscreen_content);

		// Set up an instance of SystemUiHider to control the system UI for
		// this activity.
		mSystemUiHider = SystemUiHider.getInstance(this, contentView,
				HIDER_FLAGS);
		mSystemUiHider.setup();
		mSystemUiHider
				.setOnVisibilityChangeListener(new SystemUiHider.OnVisibilityChangeListener() {
					// Cached values.
					int mControlsHeight;
					int mShortAnimTime;

					@Override
					@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
					public void onVisibilityChange(boolean visible) {
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
							// If the ViewPropertyAnimator API is available
							// (Honeycomb MR2 and later), use it to animate the
							// in-layout UI controls at the bottom of the
							// screen.
							if (mControlsHeight == 0) {
								mControlsHeight = controlsView.getHeight();
							}
							if (mShortAnimTime == 0) {
								mShortAnimTime = getResources().getInteger(
										android.R.integer.config_shortAnimTime);
							}
							controlsView
									.animate()
									.translationY(visible ? 0 : mControlsHeight)
									.setDuration(mShortAnimTime);
						} else {
							// If the ViewPropertyAnimator APIs aren't
							// available, simply show or hide the in-layout UI
							// controls.
							controlsView.setVisibility(visible ? View.VISIBLE
									: View.GONE);
						}

						if (visible && AUTO_HIDE) {
							// Schedule a hide().
							delayedHide(AUTO_HIDE_DELAY_MILLIS);
						}
					}
				});

		// Set up the user interaction to manually show or hide the system UI.
		contentView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (TOGGLE_ON_CLICK) {
					mSystemUiHider.toggle();
				} else {
					mSystemUiHider.show();
				}
			}
		});

		// Upon interacting with UI controls, delay any scheduled hide()
		// operations to prevent the jarring behavior of controls going away
		// while interacting with the UI.
		findViewById(R.id.dummy_button).setOnTouchListener(
				mDelayHideTouchListener);
		
		SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		Sensor sensor_ori = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		sensorManager.registerListener(new SensorEventListener() {

			@Override
			public void onAccuracyChanged(Sensor sensor, int accuracy) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void onSensorChanged(SensorEvent event) {
				// TODO Auto-generated method stub
				float a = (float)Math.sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2]);
				if(a > 10) {
					lastA = Math.max(lastA, a);
					delay = 0;
				}
				if(lastA != 0) {
					if(delay > 10) {
						hitOnce(lastA);
						lastA = 0;
						delay = 0;
					} else {
						delay++;
					}
				}
			}
		}, sensor_ori, SensorManager.SENSOR_DELAY_GAME);
		
		(new AsyncTask<String, String, Void>() {
			
			@Override
			protected void onProgressUpdate(String... texts) {
				String wholeText = "";
				for(String text : texts) {
					wholeText += text;
				}
				setText(wholeText);
			}

			@Override
			protected Void doInBackground(String... arg0) {
				processingHits.clear();
				
				try {
					JSONObject jo;

					publishProgress("Connecting ...");
					
					jo = getJSON("game/new");
					self_id = jo.getInt("player_id");
					game_id = jo.getInt("game_id");
					
					publishProgress("Waiting for another player (Game#" + game_id + ") ...");
					
					boolean canGameStart = jo.getBoolean("can_game_start");
					while(!canGameStart) {
						jo = getJSON("game/wait/" + game_id);
						canGameStart = jo.getBoolean("can_game_start");
					}
					
					publishProgress("Game started, now launching ...");
					
					boolean gameEnded = false;
					while(!gameEnded) {
						synchronized(processingHits) {
							for(Integer hit : processingHits) {
								jo = getJSON("game/hit/" + game_id + "/" + self_id + "/" + hit);
							}
							processingHits.clear();
						}
						
						jo = getJSON("game/status/" + game_id);
						if(jo.getInt("player_1_id") == self_id) {
							publishProgress("You#" + jo.getInt("player_1_life") + "\nCom#" + jo.getInt("player_2_life"));
						} else {
							publishProgress("You#" + jo.getInt("player_2_life") + "\nCom#" + jo.getInt("player_1_life"));
						}
						if(jo.getInt("player_1_life") <= 0) {
							if(jo.getInt("player_1_id") == self_id) {
								publishProgress("You lose");
							} else {
								publishProgress("You win");
							}
							gameEnded = true;
						} else if(jo.getInt("player_2_life") <= 0) {
							if(jo.getInt("player_2_id") == self_id) {
								publishProgress("You lose");
							} else {
								publishProgress("You win");
							}
							gameEnded = true;
						}
					}
					
				} catch (Exception e) {
					// TODO Auto-generated catch block
					throw new RuntimeException(e);
				}
				return null;
			}
			
		}).execute(new String[0]);
	}
	
	JSONObject getJSON(String sub_url) throws ClientProtocolException, IOException, JSONException {
		DefaultHttpClient client = new DefaultHttpClient();
		HttpGet getR = new HttpGet("http://10.126.8.31:3000/" + sub_url);
		
		HttpResponse res = client.execute(getR);
		BufferedReader reader = new BufferedReader(new InputStreamReader(res.getEntity().getContent(), "UTF-8"));
		StringBuilder builder = new StringBuilder();
		for (String line = null; (line = reader.readLine()) != null;) {
		    builder.append(line).append("\n");
		}
		return new JSONObject(builder.toString());
	}
	
	void setText(String t) {
		((TextView)findViewById(R.id.fullscreen_content)).setText(t);
	}
	
	int self_id;
	int game_id;
	
	List<Integer> processingHits = Collections.synchronizedList(new ArrayList<Integer>());
	
	int delay = 0;
	float lastA = 0.0f;
	
	private void hitOnce(float a) {
		synchronized(processingHits) {
			processingHits.add((int)a);
		}
	}
	

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);

		// Trigger the initial hide() shortly after the activity has been
		// created, to briefly hint to the user that UI controls
		// are available.
		delayedHide(100);
	}

	/**
	 * Touch listener to use for in-layout UI controls to delay hiding the
	 * system UI. This is to prevent the jarring behavior of controls going away
	 * while interacting with activity UI.
	 */
	View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
		@Override
		public boolean onTouch(View view, MotionEvent motionEvent) {
			if (AUTO_HIDE) {
				delayedHide(AUTO_HIDE_DELAY_MILLIS);
			}
			return false;
		}
	};

	Handler mHideHandler = new Handler();
	Runnable mHideRunnable = new Runnable() {
		@Override
		public void run() {
			mSystemUiHider.hide();
		}
	};

	/**
	 * Schedules a call to hide() in [delay] milliseconds, canceling any
	 * previously scheduled calls.
	 */
	private void delayedHide(int delayMillis) {
		mHideHandler.removeCallbacks(mHideRunnable);
		mHideHandler.postDelayed(mHideRunnable, delayMillis);
	}
}
