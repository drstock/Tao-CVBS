package com.mrstockinterfaces.speedometer;

import java.util.Iterator;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

public class VelocityService extends Service implements SensorEventListener, LocationListener, GpsStatus.Listener //, GpsStatus.NmeaListener
{
	private SensorManager mSensorManager;
	private LocationManager mLocationManager;
//	private PowerManager mPowerManager;
    private Sensor mAccelerometer;
    private long mSensorTimeStamp;
    
    private GpsStatus mGpsStatus = null;

    private final static int INIT_ACC_FILTER_SMPS = 50;		// 0.5 sec
    private final static int INIT_GRV_FILTER_SMPS = 20;		// 10 sec
    private final static float INIT_MOTION_DET_HYST = 0.1f;	// 10%
    private final static float INIT_VEL_RED_FACTOR = 0.15f;	// 15%
    private final static float INIT_NO_MOTION_DET_TIME = 3.f; // 1 sec
    
    private int mAccel_filter_smps = INIT_ACC_FILTER_SMPS;
    private int mGravity_filter_smps = INIT_GRV_FILTER_SMPS;
    private float mVelocity_reduction_factor = INIT_VEL_RED_FACTOR;
    
    private Vector3fIirFilter mAccel = new Vector3fIirFilter();
    private float mVibration = 0.f;
//    private float mPrev_Vibration = 0.f;
    private float mVibration_cal = 0.f;
    private float mGravity_cal = SensorManager.GRAVITY_EARTH;
    private float mMotion_detect_hyst = INIT_MOTION_DET_HYST;
    private int mNo_motion_count;
    private int mNo_motion_thresh = (int)(INIT_NO_MOTION_DET_TIME * 8.33f);
    private boolean mMotion;
    private Vector3fIirFilter mGravity = new Vector3fIirFilter();
    private Vector3fIirFilter mLinAccel = new Vector3fIirFilter();
    private Vector3f mVelocity_vec = new Vector3f();
    private boolean mCalibrating = true;
    private int mCal_sample_count = 0;
    private float mVelocity_precision = 1.f;
    
//    private float mVelocity_at_last_gps_fix;
    private long mGps_prev_fix_time = 0;
    private float mGps_prev_fix_accuracy = 1000.f;
    private float mGps_precision = 0.f;
    private float mGps_speed = 0.f;
    
    private float mSpeed;

    private Messenger mGuiClient = null;
    private Messenger mTransmitService = null;
    
    private Context mContext;
    
    static final int MSG_REGISTER_GUI_CLIENT = 1;
    static final int MSG_UNREGISTER_GUI_CLIENT = 2;
    static final int MSG_REGISTER_TX_CLIENT = 3;
    static final int MSG_UNREGISTER_TX_CLIENT = 4;
//    static final int MSG_REGISTER_VS_SRV = 5;
//    static final int MSG_UNREGISTER_VS_SRV = 6;
    static final int MSG_SET_ACC_FLT_SMPS = 10;
    static final int MSG_SET_GRV_FLT_SMPS = 11;
    static final int MSG_SET_VEL_RED_FACTOR = 12;
    static final int MSG_SET_MOTION_DET_HYST = 13;
    static final int MSG_CALIBRATE = 20;
//    static final int MSG_SAMPLE_RATE = 30;
    static final int MSG_ACC_VEC = 31;
    static final int MSG_GRV_VEC = 32;
    static final int MSG_LIN_ACC_VEC = 33;
    static final int MSG_VEL_VEC = 34;
    static final int MSG_SPEED = 35;
    static final int MSG_GPS_LOC = 36;
    static final int MSG_VIBRATION_CAL = 37;
    
    
    class IncomingHandler extends Handler
    {
    	public void handleMessage(Message msg)
    	{
    		switch(msg.what)
    		{
    			case MSG_REGISTER_GUI_CLIENT:
    				mGuiClient = msg.replyTo;
    				break;
    			case MSG_UNREGISTER_GUI_CLIENT:
    				mGuiClient = null;
    				break;
    			case MSG_REGISTER_TX_CLIENT:
    				mTransmitService = msg.replyTo;
    				break;
    			case MSG_UNREGISTER_TX_CLIENT:
    				mTransmitService = null;
    				break;
    			case MSG_SET_ACC_FLT_SMPS:
    				set_accel_fliter_samples(msg.arg1);
    				break;
    			case MSG_SET_GRV_FLT_SMPS:
    				set_gravity_fliter_samples(msg.arg1);
    				break;
    			case MSG_SET_VEL_RED_FACTOR:
    				mVelocity_reduction_factor = msg.arg1 / 1000.f;
    				break;
    			case MSG_SET_MOTION_DET_HYST:
    				mMotion_detect_hyst = msg.arg1 / 100.f; 
    				break;
    			case MSG_CALIBRATE:
    				calibrate();
    				break;
    			default:
    				super.handleMessage(msg);
    		}
    	}
    }

    private Messenger mIncomingMessenger = new Messenger(new IncomingHandler());
    
    
    @Override
    public void onCreate()
    {
    	Log.d("VelocityServie", "onCreate");
    	mContext = getApplicationContext();
    	
        // Get an instance of the SensorManager
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // Get an instance of the LocationManager (for GPS)
        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        
        // Get an instance of the PowerManager
//        mPowerManager = (PowerManager) getSystemService(POWER_SERVICE);

        mAccel.set_limit(mAccel_filter_smps);
        mLinAccel.set_limit(2);
    	mGravity.set_limit(mGravity_filter_smps);

        reset_vectors();

    	Toast.makeText(mContext, "VelocityService starting", Toast.LENGTH_SHORT).show();        	        	    			

    	if (!mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST))
        {
        	Toast.makeText(mContext, "No Accel", Toast.LENGTH_SHORT).show();        	
        }
        
 /*       if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
        {
        	Toast.makeText(mContext, "No GPS", Toast.LENGTH_SHORT).show();        	        	
        }
    	register_with_GPS(); */
    	
    	mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0.f, this);
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {            	
    	Log.d("VelocityServie", "onStartCommand");
        
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy()
    {
    	Log.d("VelocityServie", "onDestroy");
    	Toast.makeText(mContext, "VelocityService stopping", Toast.LENGTH_SHORT).show();        	        	    			

    	unregister_GPS();
    	mSensorManager.unregisterListener(this); 
    }
    
    @Override
    public IBinder onBind(Intent intent)
    {
    	Log.d("VelocityServie", "onBind");
    	return mIncomingMessenger.getBinder();
    }
    
    private void register_with_GPS()
    {
    	try
    	{
    		if(mLocationManager.addGpsStatusListener(this)) // && mLocationManager.addNmeaListener(this))
    		{
            	Toast.makeText(mContext, "GPS Connected", Toast.LENGTH_SHORT).show();        	        	    			
    		}
    	}
    	catch (SecurityException e)
    	{
        	Toast.makeText(mContext, "No permission to use GPS", Toast.LENGTH_SHORT).show();        	        	    			    		
    	}
    }
    
    private void unregister_GPS()
    {
    	mLocationManager.removeGpsStatusListener(this);
 //   	mLocationManager.removeNmeaListener(this);
    }
    
    private void set_accel_fliter_samples(int samples)
    {
    	if (samples != mAccel_filter_smps)
    	{
			mAccel.set_limit(mAccel_filter_smps);
	        mLinAccel.set_limit(2);
			mAccel.reset();
			mLinAccel.reset();
			mAccel_filter_smps = samples;
			mNo_motion_thresh = (int)(INIT_NO_MOTION_DET_TIME * 100.f / samples);
    	}
    } 
    
    private void set_gravity_fliter_samples(int samples)
    {
    	if (samples != mGravity_filter_smps)
    	{
    		mGravity.set_limit(samples);
    		mGravity_filter_smps = samples;
    		calibrate();
    	}
    }
    
    private void reset_vectors()
    {
    	mVelocity_vec.set(0.f, 0.f, 0.f);
        mSpeed = 0.f;
    }
    
    private void compute_accel_vector(float[] values)
    {
    	mAccel.in(values);
    	
		mVibration = ((mVibration * (mAccel.count - 1)) + mAccel.hp_out().mag()) / mAccel.count;
    }
		
    private void compute_motion()
    {
		if (mCalibrating)
		{
			mVibration_cal = Math.max(mVibration, mVibration_cal);
			send_vibration_cal_msg();
		} 

		float motion = (float)Math.abs(mAccel.mag() - mGravity_cal) + mVibration;
		
		if (!mMotion && (motion > (mVibration_cal * (1.f + mMotion_detect_hyst))))
		{
			mMotion = true;
			mNo_motion_count = 0;
		} else if (mMotion && (motion < mVibration_cal))
		{
			if (mNo_motion_count > mNo_motion_thresh)
			{
				mMotion = false;
			} else {
				mNo_motion_count++;
			}
		}
    }
    
    private void compute_gravity_vector()
    {
    	if (mCalibrating || (!mMotion))
    	{
    		mGravity.in(mAccel);
//    		mCal_sample_count = Math.min(mCal_sample_count + 1, mGravity_filter_smps * 2);
    		mCal_sample_count = 0;
    	} else {
//    		mCal_sample_count = Math.max(mCal_sample_count - 1, 0);
    		mCal_sample_count++;
    	}
    	
    	if (mCalibrating)
    	{	
    		int cal_count = mGravity.count;
    		mGravity_cal = ((mGravity_cal * cal_count) + mGravity.mag()) / (cal_count + 1.f);
    				
    		if (mGravity.at_limit())
    		{
        		mCalibrating = false;
        		cal_count = 0;
            	Toast.makeText(mContext, "Calibration done", Toast.LENGTH_SHORT).show();
    		}
    		send_calibrate_msg(cal_count);
    	}    	
    	send_gravity_msg();
    }
    
    private void compute_linear_acceleration()
    {
    	// compute the "rejection" of the accel_vec and the gravity vector. This is the projection of the accel_vec onto a plane perpendicular to the gravity vector
    	mLinAccel.in(mAccel.rej(mGravity));    
    	
    	send_lin_accel_msg();
    }
    
    private void compute_velocity_vector(float dt)
    {
    	float spd_lim = (float)Math.pow(20 - mVelocity_vec.mag(), 2) / 400.f;
    	Vector3f dv = mLinAccel.s_mult(dt * spd_lim);
    	mVelocity_vec = mVelocity_vec.sum(dv);
    	    	    	
    	if (!mMotion)
    	{
    		mVelocity_vec = mVelocity_vec.s_mult(1.f - mVelocity_reduction_factor);
    	}
    	
    	mVelocity_precision = 1.f / (1.f + (mCal_sample_count / (float)mGravity_filter_smps)); 

    	send_velocity_vector_msg();
    }
    
    private void compute_speed()
    {
    	float tot_weight = mGps_precision + mVelocity_precision;
    	float gps_weight = mGps_precision / tot_weight;
    	float vel_weight = mVelocity_precision / tot_weight;
    	
    	float speed_ms = (mVelocity_vec.mag() * vel_weight) + (mGps_speed * gps_weight);
    	mSpeed = speed_ms * 3.6f;	// change scale to km/h
    }
    
    public void onSensorChanged(SensorEvent event)
    {
        switch (event.sensor.getType())
        {
        	case Sensor.TYPE_ACCELEROMETER:
                compute_accel_vector(event.values);
                
                if (mAccel.at_limit())
                {
                    compute_motion();
                    
                	send_accel_msg();
                	
            		// calculate time-delta in seconds
            		float dt = (event.timestamp - mSensorTimeStamp) / 1000000000.f;  
                    mSensorTimeStamp = event.timestamp;
                    
	               	compute_gravity_vector();
	               	
	               	compute_linear_acceleration();
	                
            		if (!mCalibrating)
	                {
	                	compute_velocity_vector(dt);
	                	compute_speed();

	                	send_speed_msg();
	                }
	                
	                mAccel.reset();
                }                	
        		break;
        }
    }
    
    public void onAccuracyChanged(Sensor sensor, int accuracy) 
    {
    	/* do nothing */
    }
    
    public void onStatusChanged(String provider, int status, Bundle extras)
    {
    	Log.d("VelocityServie", "onStatusChanged - " + provider);
    	
    	switch (status)
    	{
    		case LocationProvider.OUT_OF_SERVICE:
            	Toast.makeText(mContext, provider + " out of service", Toast.LENGTH_SHORT).show();
            	break;
    		case LocationProvider.AVAILABLE:
            	Toast.makeText(mContext, provider + " available", Toast.LENGTH_SHORT).show();
            	break;
    		case LocationProvider.TEMPORARILY_UNAVAILABLE:
            	Toast.makeText(mContext, provider + " temporarily unavailable", Toast.LENGTH_SHORT).show();
            	break;
    	}
    }
    
    public void onProviderEnabled(String provider)
    {
    	Log.d("VelocityServie", "onProviderEnabled - " + provider);
    	Toast.makeText(mContext, provider + " enabled", Toast.LENGTH_SHORT).show();
    	register_with_GPS();
    }
    
    public void onProviderDisabled(String provider)
    {
    	Log.d("VelocityServie", "onProviderDisabled - " + provider);
    	Toast.makeText(mContext, provider + " disabled", Toast.LENGTH_SHORT).show();
    	unregister_GPS();
    }
    
    public void onLocationChanged(Location location)
    {
    	Log.d("VelocityService", "onLocationChanged");
    
    	long time = location.getTime();
    	float dt = (time - mGps_prev_fix_time) / 1000.f;
    	mGps_prev_fix_time = time;
    	
    	float accuracy = location.getAccuracy();
    	mGps_precision = Math.min(8.f / (dt * (mGps_prev_fix_accuracy + accuracy)), 1.f);
    	mGps_prev_fix_accuracy = accuracy;
    	
    	mGps_speed = location.getSpeed();
    	
//    	mVelocity_at_last_gps_fix = mVelocity_vec.mag();
    	
    	send_location_msg(location);    	
    }
    
    public void onGpsStatusChanged(int status)
    {
    	Log.d("VelocityServie", "onGpsStatusChanged");
    	mGpsStatus = mLocationManager.getGpsStatus(mGpsStatus);
    	
    	switch (status)
    	{
    		case GpsStatus.GPS_EVENT_STARTED:
            	Toast.makeText(mContext, "GPS on", Toast.LENGTH_SHORT).show();        	        	
    			break;
    		case GpsStatus.GPS_EVENT_STOPPED:
            	Toast.makeText(mContext, "GPS off", Toast.LENGTH_SHORT).show();        	        	
    			break;
    		case GpsStatus.GPS_EVENT_FIRST_FIX:
            	Toast.makeText(mContext, "Have GPS fix", Toast.LENGTH_SHORT).show();        	        	
    			break;
    		case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
    			Iterator<GpsSatellite> sats = mGpsStatus.getSatellites().iterator();
//    			String satlist = "GPS sats:\n";
//    			int satcount = 0;
    			while (sats.hasNext())
    			{
    				GpsSatellite sat = sats.next();
//    				satlist += String.format("%d: %.1f\n", sat.getPrn(), sat.getSnr());
//    				satcount++;
    				Log.d("VelocityService", String.format("GPS sat %d: %.1f", sat.getPrn(), sat.getSnr()));
    			}
/*    			if (satcount > 0)
    			{
    				Toast.makeText(mContext, satlist, Toast.LENGTH_SHORT).show();
    			} */
    			break;
    	}
    }
    
/*    public void onNmeaReceived(long timestamp, String nmea)
    {
    	Log.d("VelocityServie", "onNmeaReceived: " + nmea);
    	
    } */
    
    public void calibrate()
    {
    	mCalibrating = true;
    	mVibration_cal = 0.f;
    	mGravity.reset();
    	mLinAccel.reset();
    	reset_vectors();
    }
    
    private void send_calibrate_msg(int count)
    {
		if (mGuiClient != null)
		{
    		Message msg = Message.obtain(null, MSG_CALIBRATE, count, 0);
    		try
    		{
    			mGuiClient.send(msg);
    		}
    		catch (RemoteException e)
    		{
    			mGuiClient = null;
    		}
		}    	
    }
    
    private void send_gravity_msg()
    {
		if (mGuiClient != null)
		{
			Message msg;
			if (mMotion)
			{
				msg = Message.obtain(null, MSG_GRV_VEC, 1, 0, (Object)mGravity.lp_out());
			} else {
				msg = Message.obtain(null, MSG_GRV_VEC, 0, 0, (Object)mGravity.lp_out());				
			}
			
    		try
    		{
    			mGuiClient.send(msg);
    		}
    		catch (RemoteException e)
    		{
    			mGuiClient = null;
    		}
		}    	
    }
    
    private void send_accel_msg()
    {
		if (mGuiClient != null)
		{
    		Message msg = Message.obtain(null, MSG_ACC_VEC, (int)(Math.max(mVibration - mVibration_cal, 0.f) * 1000.f), 0, (Object)mAccel.lp_out());
    		try
    		{
    			mGuiClient.send(msg);
    		}
    		catch (RemoteException e)
    		{
    			mGuiClient = null;
    		}
		}   	
    }
    
    private void send_vibration_cal_msg()
    {
		if (mGuiClient != null)
		{
    		Message msg = Message.obtain(null, MSG_VIBRATION_CAL, (int)(mVibration_cal * 1000.f), 0);
    		try
    		{
    			mGuiClient.send(msg);
    		}
    		catch (RemoteException e)
    		{
    			mGuiClient = null;
    		}
		}   	
    }
    
    private void send_lin_accel_msg()
    {
		if (mGuiClient != null)
		{
    		Message msg = Message.obtain(null, MSG_LIN_ACC_VEC, (Object)mLinAccel.lp_out());
    		try
    		{
    			mGuiClient.send(msg);
    		}
    		catch (RemoteException e)
    		{
    			mGuiClient = null;
    		}
		}    	
    }
    
    private void send_velocity_vector_msg()
    {
		if (mGuiClient != null)
		{
    		Message msg = Message.obtain(null, MSG_VEL_VEC, (int)(mVelocity_precision * 1000.f), 0, (Object)mVelocity_vec);
    		try
    		{
    			mGuiClient.send(msg);
    		}
    		catch (RemoteException e)
    		{
    			mGuiClient = null;
    		}
		}    	
    }
    
    private void send_speed_msg()
    {
		Message msg = Message.obtain(null, MSG_SPEED, (int)(mSpeed * 10.f), 0);

		if (mTransmitService != null)
		{
    		try
    		{
    			mTransmitService.send(msg);
    		}
    		catch (RemoteException e)
    		{
    			mTransmitService = null;
    		}
		}

    	if (mGuiClient != null)
		{
    		try
    		{
    			mGuiClient.send(msg);
    		}
    		catch (RemoteException e)
    		{
    			mGuiClient = null;
    		}
		}    	
    }
    
    private void send_location_msg(Location location)
    {
		Message msg = Message.obtain(null, MSG_GPS_LOC, (int)(mGps_precision * 1000.f) ,0 ,(Object)location);
    	
    	if (mGuiClient != null)
		{
    		try
    		{
    			mGuiClient.send(msg);
    		}
    		catch (RemoteException e)
    		{
    			mGuiClient = null;
    		}
		}    	
    }
}

