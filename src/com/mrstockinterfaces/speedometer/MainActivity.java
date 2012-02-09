package com.mrstockinterfaces.speedometer;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity
{
	Messenger mVelService = null;

    private final static float INIT_ACC_FILTER_RESP_TIME = 0.05f;
    private final static int INIT_MOT_DET_HYST = 10;
    private final static float INIT_GRV_FILTER_RESP_TIME = 10.f;
    private final static float INIT_VEL_RED_FACTOR = 0.15f;
    
    private long mPrevTimestamp = 0;
    
    private ProgressBar mProgress;
    
    private SeekBar mAccel_filter_slider;
    private SeekBar mMotion_det_hyst_slider;
    private SeekBar mGravity_filter_slider;
    private SeekBar mVelocity_filter_slider;

    private float mAccel_filter_interval = INIT_ACC_FILTER_RESP_TIME;
    private int mMotion_det_hyst_pct = INIT_MOT_DET_HYST;
    private float mGravity_filter_interval = INIT_GRV_FILTER_RESP_TIME;
    private float mVelocity_reduction_factor = INIT_VEL_RED_FACTOR;
    
    private SharedPreferences mPrefs;
    private SharedPreferences.Editor mPrefsEdit;
    
    class IncomingHandler extends Handler
    {
    	@Override
    	public void handleMessage(Message msg)
    	{
    		TextView tv;
    		Vector3f vec;
    		Location loc;
    		switch (msg.what)
    		{
    			case VelocityService.MSG_CALIBRATE:
    				if (msg.arg1 > 0)
    				{
	    				if (mProgress.getVisibility() == View.INVISIBLE)
	    				{
	    					mProgress.setVisibility(View.VISIBLE);
	    				}
	        			mProgress.setProgress(msg.arg1);
    				} else {
    					mProgress.setVisibility(View.INVISIBLE);
    				}
    				break;
    			case VelocityService.MSG_ACC_VEC:
    				vec = (Vector3f)msg.obj;
    		    	tv = (TextView)findViewById(R.id.accel_val);
    		    	tv.setText(String.format("%.3f", vec.mag()));
//    		    	tv = (TextView)findViewById(R.id.vibr_val);
//    		    	tv.setText(String.format("%.3f", msg.arg1 / 1000.f));
    		    	break;
    			case VelocityService.MSG_LIN_ACC_VEC:
    				vec = (Vector3f)msg.obj;
    		    	tv = (TextView)findViewById(R.id.lin_accel_val);
    		    	tv.setText(String.format("%.3f", vec.mag()));
    		    	break;
    			case VelocityService.MSG_VIBRATION_CAL:
    		    	tv = (TextView)findViewById(R.id.vibr_val);
    		    	tv.setText(String.format("%.3f", msg.arg1 / 1000.f));
    		    	break;
    			case VelocityService.MSG_VEL_VEC:
    				vec = (Vector3f)msg.obj;
    		    	tv = (TextView)findViewById(R.id.vel_val);
    		    	tv.setText(String.format("%.3f", vec.mag()));
    		    	tv = (TextView)findViewById(R.id.vel_accuracy_val);
    		    	tv.setText(String.format("%.3f", msg.arg1 / 1000.f));
    		    	break;
    			case VelocityService.MSG_SPEED:
    		    	tv = (TextView)findViewById(R.id.speed_val);
    		    	tv.setText(String.format("%.1f", msg.arg1 / 10.f));
    				
					long time = System.currentTimeMillis();
    				if (mPrevTimestamp > 0)
    				{
    					float sr = 1000.f / (time - mPrevTimestamp);
        		    	tv = (TextView)findViewById(R.id.samplerate_val);
        		    	tv.setText(String.format("%.2f", sr));   				
    				}
					mPrevTimestamp = time;
    				break;
    			case VelocityService.MSG_GRV_VEC:
    				vec = (Vector3f)msg.obj;
    		    	tv = (TextView)findViewById(R.id.grv_val);
    		    	tv.setText(String.format("%.3f", vec.mag()));
    		    	tv = (TextView)findViewById(R.id.grv_x_val);
    		    	tv.setText(String.format("%.3f", vec.x));
    		    	tv = (TextView)findViewById(R.id.grv_y_val);
    		    	tv.setText(String.format("%.3f", vec.y));
    		    	tv = (TextView)findViewById(R.id.grv_z_val);
    		    	tv.setText(String.format("%.3f", vec.z));
    		    	tv = (TextView)findViewById(R.id.motion_val);
    		    	tv.setText(String.format("%d", msg.arg1));
    		    	
    				break;
    			case VelocityService.MSG_GPS_LOC:
    				loc = (Location)msg.obj;
    		    	tv = (TextView)findViewById(R.id.gps_spd_val);
    		    	tv.setText(String.format("%.1f", loc.getSpeed() * 3.6f));
    		    	tv = (TextView)findViewById(R.id.gps_accuracy_val);
    		    	tv.setText(String.format("%.3f", msg.arg1 / 1000.f));
    		    	break;
    			default:
    				super.handleMessage(msg);
    		}
    	}
    }
    
    final Messenger mIncomingMessenger = new Messenger(new IncomingHandler());
    
    private boolean register_with_VelocityService()
    {
    	if (mVelService == null)
    	{
    		return false;
    	}
    	
		Message msg = Message.obtain(null, VelocityService.MSG_REGISTER_GUI_CLIENT);
		msg.replyTo = mIncomingMessenger;
		
		try {
			mVelService.send(msg);
			return true;
		}
		catch (RemoteException e)
		{
			return false;
		}
    }
    
    private boolean unregister_from_VelocityService()
    {
    	if (mVelService == null)
    	{
    		return false;
    	}
    	
		Message msg = Message.obtain(null, VelocityService.MSG_UNREGISTER_GUI_CLIENT);
		msg.replyTo = mIncomingMessenger;
		
		try {
			mVelService.send(msg);
			return true;
		}
		catch (RemoteException e)
		{
			return false;
		}
    }
    
    private ServiceConnection mVelServiceConnection = new ServiceConnection()
    {
    	public void onServiceConnected(ComponentName class_name, IBinder service)
    	{
    		mVelService = new Messenger(service);
    		
    		if (!register_with_VelocityService())
    		{
    			return;
    		}
    		
    		mAccel_filter_interval = mPrefs.getFloat("Accel_filter_interval", INIT_ACC_FILTER_RESP_TIME);
    		mMotion_det_hyst_pct = mPrefs.getInt("Motion_detect_hysteresis", INIT_MOT_DET_HYST);
    		mGravity_filter_interval = mPrefs.getFloat("Gravity_filter_interval", INIT_GRV_FILTER_RESP_TIME);
    		mVelocity_reduction_factor = mPrefs.getFloat("Velocity_reduction_factor", INIT_VEL_RED_FACTOR);
    		
        	set_accel_filter_time(mAccel_filter_interval);	// this call also sets gravity filter interval
        	set_gravity_filter_time(mGravity_filter_interval);
        	set_motion_detect_hyst(mMotion_det_hyst_pct);
        	set_velocity_reduction_factor(mVelocity_reduction_factor);
    		
    		Toast.makeText(MainActivity.this, "connected to VelocityService", Toast.LENGTH_SHORT).show();
    	}
    	
    	public void onServiceDisconnected(ComponentName class_name)
    	{
    		mVelService = null;
    		Toast.makeText(MainActivity.this, "disconnected from VelocityService", Toast.LENGTH_SHORT).show();
    	}
    };
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.main);

        // Get an instance of the PowerManager
//        mPowerManager = (PowerManager) getSystemService(POWER_SERVICE);
     
        mProgress = (ProgressBar)findViewById(R.id.cal_progress);
        
    	mAccel_filter_slider = (SeekBar)findViewById(R.id.acc_flt_slider);
    	mAccel_filter_slider.setMax(45);
    	mAccel_filter_slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
    	{
			public void onStopTrackingTouch(SeekBar seekBar)
			{
				// TODO Auto-generated method stub
			}
			
			public void onStartTrackingTouch(SeekBar seekBar)
			{
				// TODO Auto-generated method stub
			}
			
			public void onProgressChanged(SeekBar s, int val, boolean fromUser)
			{
				if (fromUser)
				{
					set_accel_filter_time(0.05f + (val / 100.0f));
				}
			}
		});

    	mMotion_det_hyst_slider = (SeekBar)findViewById(R.id.mot_det_hyst_slider);
    	mMotion_det_hyst_slider.setMax(100);
    	mMotion_det_hyst_slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
    	{
			public void onStopTrackingTouch(SeekBar seekBar)
			{
				// TODO Auto-generated method stub
			}
			
			public void onStartTrackingTouch(SeekBar seekBar)
			{
				// TODO Auto-generated method stub
			}
			
			public void onProgressChanged(SeekBar s, int val, boolean fromUser)
			{
				if (fromUser)
				{
					set_motion_detect_hyst(val);
				}
			}
		});

    	mGravity_filter_slider = (SeekBar)findViewById(R.id.grv_flt_slider);
    	mGravity_filter_slider.setMax(90);
    	mGravity_filter_slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
    	{
			public void onStopTrackingTouch(SeekBar seekBar)
			{
				// TODO Auto-generated method stub
			}
			
			public void onStartTrackingTouch(SeekBar seekBar)
			{
				// TODO Auto-generated method stub
			}
			
			public void onProgressChanged(SeekBar s, int val, boolean fromUser)
			{
				if (fromUser)
				{
					set_gravity_filter_time(1.0f + (val / 10.0f));
				}
			}
		});

    	mVelocity_filter_slider = (SeekBar)findViewById(R.id.vel_flt_slider);
    	mVelocity_filter_slider.setMax(100);
    	mVelocity_filter_slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
    	{
			public void onStopTrackingTouch(SeekBar seekBar)
			{
				// TODO Auto-generated method stub
			}
			
			public void onStartTrackingTouch(SeekBar seekBar)
			{
				// TODO Auto-generated method stub
			}
			
			public void onProgressChanged(SeekBar s, int val, boolean fromUser)
			{
				if (fromUser)
				{
					set_velocity_reduction_factor(val / 100.0f);
				}
			}
		});
    	
    	mPrefs = getSharedPreferences("SpeedometerPrefs", Context.MODE_PRIVATE);
    	mPrefsEdit = mPrefs.edit();
    	
    	start_sensor_service();
    }
    
/*    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.optionsmenu, menu);
        return true;
    }    
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
        case R.id.settings:
            do_settings();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    } */    
    
    private void start_sensor_service()
    {
    	Intent intent = new Intent(MainActivity.this, VelocityService.class);
    	startService(intent);
    	bindService(intent, mVelServiceConnection, Context.BIND_AUTO_CREATE);
    }
    
    private void stop_sensors()
    {
    	Intent intent = new Intent(MainActivity.this, VelocityService.class);
    	unregister_from_VelocityService();
    	unbindService(mVelServiceConnection);
    	stopService(intent);
    }
    
    
    public void set_accel_filter_time(float interval)
    {
    	int samples = (int)(interval * 100);
    	mAccel_filter_slider.setProgress((int)((interval - 0.05f) * 100.0f));
    	
    	TextView tv = (TextView)findViewById(R.id.acc_flt_resp_time_val);
    	tv.setText(String.valueOf((int)(interval * 1000.f)));
    	
    	if (mVelService != null)
    	{
	    	Message msg = Message.obtain(null, VelocityService.MSG_SET_ACC_FLT_SMPS, samples, 0);
	    	try {
	    		mVelService.send(msg);
	    	}
	    	catch (RemoteException e)
	    	{
	    		mVelService = null;
	    	}
    	}
    	
    	if (interval != mAccel_filter_interval)
    	{
	    	mPrefsEdit.putFloat("Accel_filter_interval", interval);
	    	mPrefsEdit.apply();
    	}
    	mAccel_filter_interval = interval;
    	
   		set_gravity_filter_time(mGravity_filter_interval);
    } 
    
    public void set_motion_detect_hyst(int percent)
    {
    	mMotion_det_hyst_slider.setProgress(percent);
    	
    	TextView tv = (TextView)findViewById(R.id.mot_det_hyst_val);
    	tv.setText(String.valueOf(percent));
    	
    	if (mVelService != null)
    	{
	    	Message msg = Message.obtain(null, VelocityService.MSG_SET_MOTION_DET_HYST, percent, 0);
	    	try {
	    		mVelService.send(msg);
	    	}
	    	catch (RemoteException e)
	    	{
	    		mVelService = null;
	    	}
    	}
    	
    	if (percent != mMotion_det_hyst_pct)
    	{
	    	mPrefsEdit.putInt("Motion_detect_hysteresis", percent);
	    	mPrefsEdit.apply();
    	}
    	mMotion_det_hyst_pct = percent;    	
   }
    
    public void set_gravity_filter_time(float interval)
    {
    	int samples = (int)(interval * 8.33f);
    	mGravity_filter_slider.setProgress((int)((interval - 1.0f) * 10.0f));
    	mProgress.setMax(samples);

    	TextView tv = (TextView)findViewById(R.id.grv_flt_resp_time_val);
    	tv.setText(String.valueOf(interval));
    	
    	if (mVelService != null)
    	{
	    	Message msg = Message.obtain(null, VelocityService.MSG_SET_GRV_FLT_SMPS, samples, 0);
	    	try {
	    		mVelService.send(msg);
	    	}
	    	catch (RemoteException e)
	    	{
	    		mVelService = null;
	    	}
    	}

    	if (interval != mGravity_filter_interval)
    	{
	    	mPrefsEdit.putFloat("Gravity_filter_interval", interval);
	    	mPrefsEdit.apply();
    	}
    	mGravity_filter_interval = interval;
   }
    
    public void set_velocity_reduction_factor(float factor)
    {
    	mVelocity_filter_slider.setProgress((int)(factor * 100.0f));

    	TextView tv = (TextView)findViewById(R.id.vel_red_factor_val);
    	tv.setText(String.format("%.2f", factor));

    	if (mVelService != null)
    	{
	    	Message msg = Message.obtain(null, VelocityService.MSG_SET_VEL_RED_FACTOR, (int)(factor * 1000.f), 0);
	    	try {
	    		mVelService.send(msg);
	    	}
	    	catch (RemoteException e)
	    	{
	    		mVelService = null;
	    	}
    	}

    	if (factor != mVelocity_reduction_factor)
    	{
	    	mPrefsEdit.putFloat("Velocity_reduction_factor", factor);
	    	mPrefsEdit.apply();
    	}
        mVelocity_reduction_factor = factor;
    }
    
 /*   private void do_settings()
    {
    	
    } */
    
  
    protected void onResume()
    {
        super.onResume();
        if (register_with_VelocityService())
        {
    		Toast.makeText(MainActivity.this, "VelocityService connected", Toast.LENGTH_SHORT).show();	
        }
    }

    protected void onPause() 
    {
        super.onPause();
        if (unregister_from_VelocityService())
        {
    		Toast.makeText(MainActivity.this, "VelocityService disconnected", Toast.LENGTH_SHORT).show();	
        }
    }
 
   
    public void calibrate(View v)
    {
    	if (mVelService != null)
    	{
	    	Message msg = Message.obtain(null, VelocityService.MSG_CALIBRATE);
	    	try {
	    		mVelService.send(msg);
	    	}
	    	catch (RemoteException e)
	    	{
	    		mVelService = null;
	    	}
    	}
    }
}