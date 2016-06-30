package org.zywx.wbpalmstar.plugin.uexpedometer.pedometer;

import org.zywx.wbpalmstar.engine.universalex.EUExUtil;
import org.zywx.wbpalmstar.plugin.uexpedometer.utils.MLog;
import org.zywx.wbpalmstar.plugin.uexpedometer.utils.SensorUtil;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;

/**
 * 计步服务
 * 
 * @author waka
 * @version createTime:2016年6月29日 下午2:46:48
 */
public class PedometerService extends Service implements SensorEventListener {

	// 传感器
	private SensorManager mSensorManager;// 传感器管理器
	private Sensor mSensor;// 最终使用的传感器
	private int mSensorType;// 最终使用的传感器类型，作为一个判断标识

	// 步数处理
	private StepDataProcess mStepDataProcess;// 数据处理类
	private int mStep;// 步数

	/* 通知 */
	private static final int NOTIFICATION_STEP_SERVICE = 1;// 通知标记
	private static final String NOTIFICATION_STEP_SERVICE_TICKER = EUExUtil.getString("plugin_pedometer_step_service_ticker");// 通知ticker，在通知刚生成时在手机最上方弹出的一闪而过的提示
	private static final String NOTIFICATION_STEP_SERVICE_TITLE = EUExUtil.getString("plugin_pedometer_step_number");// 通知标题
	private static final String NOTIFICATION_STEP_SERVICE_CONTENT = EUExUtil.getString("plugin_pedometer_step_calorie");// 通知内容
	private static final float SCALE_STEP_CALORIES = 43.22f;
	private NotificationManager mNotificationManager;
	private Notification.Builder mBuilder;
	private Notification mNotification;
	// 监听通知栏被删除
	private static final String ACTION_NOTIFICATION_DELETE = "org.zywx.wbpalmstar.plugin.uexpedometer.NOTIFICATION_DELETE";// 通知栏被删除Action
	private boolean mUpdateNotiSwitch = true;// 更新通知栏开关，为true更新通知栏，为false不更新
	private BroadcastReceiver mNotiDelReceiver = new BroadcastReceiver() {// 广播接收器，用来接收通知被删除的广播
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent == null || context == null) {
				return;
			}
			if (intent.getAction().equals(ACTION_NOTIFICATION_DELETE)) {// 如果是通知被删除的广播
				mNotificationManager.cancel(NOTIFICATION_STEP_SERVICE);// 关闭通知
				mUpdateNotiSwitch = false;// 通知栏开关值为false
			}
		}
	};

	@Override
	/**
	 * onBind
	 */
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	/**
	 * onCreate
	 */
	public void onCreate() {
		super.onCreate();

		MLog.getIns().d("");

		// 传感器管理器
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

		// 选择最好的传感器
		mSensorType = SensorUtil.getBestPedometerSensorType(this);
		mSensor = mSensorManager.getDefaultSensor(mSensorType);

		// 注册监听(使用正常延时时，加速度传感器为1s5次)
		mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);

		// 初始化数据处理类
		mStepDataProcess = new StepDataProcess(this.getApplicationContext());

		// 通知管理器
		mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

	}

	@Override
	/**
	 * onStartCommand
	 */
	public int onStartCommand(Intent intent, int flags, int startId) {

		MLog.getIns().d("");

		mUpdateNotiSwitch = true;// 更新通知栏标志值为true

		// 注册广播,监听通知栏被删除的广播
		IntentFilter intentFilter = new IntentFilter(ACTION_NOTIFICATION_DELETE);
		registerReceiver(mNotiDelReceiver, intentFilter);

		return START_STICKY;// 粘性服务，在被kill后尝试自动开启
	}

	@Override
	/**
	 * onDestroy
	 */
	public void onDestroy() {
		super.onDestroy();

		MLog.getIns().d("");

		// 取消注册删除通知广播
		unregisterReceiver(mNotiDelReceiver);

		// 注销监听
		mSensorManager.unregisterListener(this);

		// 关闭步数数据处理类
		mStepDataProcess.close();

		// 结束当前进程
		Process.killProcess(Process.myPid());
	}

	@Override
	/**
	 * 当传感器检测到的数值发生变化时就会调用这个方法
	 */
	public void onSensorChanged(SensorEvent event) {

		if (mSensorType == Sensor.TYPE_STEP_COUNTER) {
			mStep = mStepDataProcess.processDataByStepCounter(event);
		}

		if (mSensorType == Sensor.TYPE_STEP_DETECTOR) {
			mStep = mStepDataProcess.processDataByStepDetector(event);
		}

		if (mSensorType == Sensor.TYPE_ACCELEROMETER) {
			mStep = mStepDataProcess.processDataByAccelerometer(event);
		}

		MLog.getIns().i("最终步数：" + mStep);

		if (mUpdateNotiSwitch) {
			showNotification(mStep);
		}

	}

	@Override
	/**
	 * 当传感器的精度发生变化时就会调用这个方法
	 */
	public void onAccuracyChanged(Sensor sensor, int accuracy) {

		MLog.getIns().d("");

	}

	/**
	 * 显示通知
	 */
	@SuppressWarnings("deprecation")
	private void showNotification(int step) {

		mBuilder = new Notification.Builder(this);// 使用Notification.Builder创建Notification
		mBuilder.setContentTitle(step + NOTIFICATION_STEP_SERVICE_TITLE);
		mBuilder.setContentText(String.format("%.1f", (step * SCALE_STEP_CALORIES) / 1000) + NOTIFICATION_STEP_SERVICE_CONTENT);
		mBuilder.setTicker(NOTIFICATION_STEP_SERVICE_TICKER);
		mBuilder.setSmallIcon(getApplicationInfo().icon);

		// 监听通知栏被删除
		Intent deleteIntent = new Intent();
		deleteIntent.setAction(ACTION_NOTIFICATION_DELETE);
		mBuilder.setDeleteIntent(PendingIntent.getBroadcast(this, NOTIFICATION_STEP_SERVICE, deleteIntent, 0));

		if (Build.VERSION.SDK_INT >= 16) {
			mNotification = mBuilder.build();
		} else {
			mNotification = mBuilder.getNotification();
		}
		mNotificationManager.notify(NOTIFICATION_STEP_SERVICE, mNotification);
	}
}
