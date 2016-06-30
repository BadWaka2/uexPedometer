package org.zywx.wbpalmstar.plugin.uexpedometer.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;

/**
 * 传感器工具类
 * 
 * @author waka
 * @version createTime:2016年6月29日 下午3:37:59
 */
public class SensorUtil {

	/**
	 * 得到最好的计步传感器
	 * 
	 * @param context
	 * @return
	 */
	@SuppressLint("InlinedApi")
	public static int getBestPedometerSensorType(Context context) {

		SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

		// 步行总数,最理想的健康跟踪传感器，实时返回步数总数，重启手机时清空
		Sensor stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
		if (stepCounter != null) {
			MLog.getIns().i("传感器类型：TYPE_STEP_COUNTER");
			return Sensor.TYPE_STEP_COUNTER;
		}

		// 步数探测器，第二选择,适合航迹推算，每走一步+1
		Sensor stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
		if (stepDetector != null) {
			MLog.getIns().i("传感器类型：TYPE_STEP_DETECTOR");
			return Sensor.TYPE_STEP_DETECTOR;
		}

		// 加速度传感器，最差选择，软件层算法模拟，精度最低，但普及率最高
		Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		if (accelerometer != null) {
			MLog.getIns().i("传感器类型：TYPE_ACCELEROMETER");
			return Sensor.TYPE_ACCELEROMETER;
		}

		return -1;

	}

}
