package org.zywx.wbpalmstar.plugin.uexpedometer.pedometer;

import java.sql.Date;

import org.zywx.wbpalmstar.plugin.uexpedometer.Constant;
import org.zywx.wbpalmstar.plugin.uexpedometer.model.DBHelper;
import org.zywx.wbpalmstar.plugin.uexpedometer.utils.MLog;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;

/**
 * 步数数据处理类
 * 
 * @author waka
 * @version createTime:2016年6月29日 下午3:34:27
 */
public class StepDataProcess {

	private Context mContext;
	private DBHelper mDBHelper;
	private SQLiteDatabase mDB;

	private String mDateStr;// 当前日期(字符串)，当日期改变的时候需要进行归零操作
	private int mStepHistory;// 开始计步时当天的已有步数

	/* TYPE_STEP_COUNTER */
	private int mStepDiff = -1;// 步数差值

	/* TYPE_ACCELEROMETER */
	private float mSensitivity = 2; // 灵敏度 灵敏度为1的时候轻轻晃一下就计步了，灵敏度为10时得狠狠地晃才行
	private float mLastValues[] = new float[3 * 2];
	private float mScale[] = new float[2];
	private float mYOffset;
	private long mEnd = 0;
	private long mStart = 0;
	private float mLastDirections[] = new float[3 * 2];
	private float mLastExtremes[][] = { new float[3 * 2], new float[3 * 2] };
	private float mLastDiff[] = new float[3 * 2];
	private int mLastMatch = -1;
	// 步数筛选
	private int mCount = 0;// 因为加速度传感器并不精确，所以需要一个筛选机制，该变量用来计算数据变化的次数，因为1s会调用5次，所以10s就是50次，50次为一个周期
	private int mLastStep = -1;// 每次周期开始时上一次的步数
	private boolean mIsStable = false;// 是否进入稳定步行状态；稳定步行状态会将步数写入数据库，并实时更新步数
	private static final int STEP_FILTER_THRESHOLD = 10;// 步数筛选的阈值;默认为10步
	private static final int STEP_FILTER_TIME_INTERVAL = 10;// 步数筛选的时间间隔;默认为10s
	private static final int CALLBACK_TRIGGER_PER_SECOND = 5;// 传感器回调每秒触发的次数;使用SENSOR_DELAY_NORMAL时为每秒5次
	private static final int TOTAL_COUNT = STEP_FILTER_TIME_INTERVAL * CALLBACK_TRIGGER_PER_SECOND;// 总次数

	/**
	 * 构造方法
	 * 
	 * @param context
	 */
	public StepDataProcess(Context context) {
		mContext = context;

		// 初始化数据库
		mDBHelper = new DBHelper(mContext, Constant.DB_NAME, null, 1);
		mDB = mDBHelper.getWritableDatabase();// 得到数据库实例

		// 得到当天历史步数
		mDateStr = new Date(System.currentTimeMillis()).toString();
		mStepHistory = mDBHelper.queryStep(mDB, mDateStr);
		MLog.getIns().i("当天步数 = " + mStepHistory);
		if (mStepHistory == -1) {// 如果当天没有数据，则插入0数据
			mDBHelper.insertData(mDB, 0);
			mStepHistory = 0;
		}

		// TYPE_ACCELEROMETER
		int h = 480;
		mYOffset = h * 0.5f;
		mScale[0] = -(h * 0.5f * (1.0f / (SensorManager.STANDARD_GRAVITY * 2)));
		mScale[1] = -(h * 0.5f * (1.0f / (SensorManager.MAGNETIC_FIELD_EARTH_MAX)));
	}

	/**
	 * close
	 */
	public void close() {
		mDB.close();
		mContext = null;
		mDBHelper = null;
		mDB = null;
		mDateStr = null;
	}

	/**
	 * 通过TYPE_STEP_COUNTER
	 * 
	 * 处理数据
	 * 
	 * @param event
	 * @return
	 */
	public int processDataByStepCounter(SensorEvent event) {

		// 得到原始数据(开机以来的步行总数)
		int stepOriginal = (int) event.values[0];
		MLog.getIns().i("原始数据 = " + stepOriginal);

		// 计算步数差值
		if (mStepDiff == -1) {
			mStepDiff = stepOriginal - mStepHistory;
		}
		MLog.getIns().i("步数差值 = " + mStepDiff);

		// 计算实际步数
		int step = stepOriginal - mStepDiff;
		MLog.getIns().i("实际步数 = " + step);

		return writeStepToDB(step);
	}

	/**
	 * 通过TYPE_STEP_DETECTOR
	 * 
	 * 处理数据
	 * 
	 * @param event
	 * @return
	 */
	public int processDataByStepDetector(SensorEvent event) {

		// 得到原始数据(如果values[0]的值等于1)，则加一步
		if (event.values[0] == 1) {
			mStepHistory++;
			MLog.getIns().i("实际步数 = " + mStepHistory);
		}
		return writeStepToDB(mStepHistory);

	}

	/**
	 * 通过TYPE_ACCELEROMETER
	 * 
	 * 处理数据
	 * 
	 * 使用正常延时的话，是1s五次
	 * 
	 * @param event
	 * @return
	 */
	public int processDataByAccelerometer(SensorEvent event) {

		// 周期开始且上一次的步数未初始化
		if (mCount == 0 && mLastStep == -1) {
			mLastStep = mStepHistory;// 记录开始时的数据
			MLog.getIns().i("周期开始：开始时的步数为" + mLastStep);
		}

		// 周期结束
		if (mCount == TOTAL_COUNT - 1) {

			MLog.getIns().i("周期结束：步数差值为" + (mStepHistory - mLastStep));

			// 如果筛选时间之内步数的变化值没有超过步数过滤阈值
			if ((mStepHistory - mLastStep) < STEP_FILTER_THRESHOLD) {

				// 如果不是稳定状态
				if (!mIsStable) {
					mStepHistory = mLastStep;
					mLastStep = writeStepToDB(mLastStep);// 重新将上一次的步数写入数据库
				}
				// 如果是稳定状态，为了避免步数出现回退的情况
				else {
					mLastStep = mStepHistory;
				}
				mIsStable = false;// 退出稳定计步状态
				MLog.getIns().i("退出稳定计步状态");
			}

			// 如果筛选时间之内步数的变化值超过步数过滤阈值
			else {
				mLastStep = mStepHistory;// 更新上一次的步数
				mIsStable = true;// 进入稳定计步状态
				MLog.getIns().i("进入稳定计步状态");
			}

		}
		mCount++;
		mCount = mCount % TOTAL_COUNT;// 10s一个周期，50次即为10s

		// 使用算法模拟
		float vSum = 0;
		for (int i = 0; i < 3; i++) {
			final float v = mYOffset + event.values[i] * mScale[1];
			vSum += v;
		}
		int k = 0;
		float v = vSum / 3;
		float direction = (v > mLastValues[k] ? 1 : (v < mLastValues[k] ? -1 : 0));
		if (direction == -mLastDirections[k]) {
			// Direction changed
			int extType = (direction > 0 ? 0 : 1); // minumum or maximum?
			mLastExtremes[extType][k] = mLastValues[k];
			float diff = Math.abs(mLastExtremes[extType][k] - mLastExtremes[1 - extType][k]);

			if (diff > mSensitivity) {

				boolean isAlmostAsLargeAsPrevious = diff > (mLastDiff[k] * 2 / 3);
				boolean isPreviousLargeEnough = mLastDiff[k] > (diff / 3);
				boolean isNotContra = (mLastMatch != 1 - extType);
				if (isAlmostAsLargeAsPrevious && isPreviousLargeEnough && isNotContra) {
					mEnd = System.currentTimeMillis();

					if (mEnd - mStart > 500) {// 此时判断为走了一步
						// 步数加1
						mStepHistory++;
						MLog.getIns().i("实际步数 = " + mStepHistory);
						mLastMatch = extType;
						mStart = mEnd;
					}
				} else {
					mLastMatch = -1;
				}
			}
			mLastDiff[k] = diff;
		}
		mLastDirections[k] = direction;
		mLastValues[k] = v;

		// 如果进入稳定步行状态
		if (mIsStable) {
			return writeStepToDB(mStepHistory);
		}
		// 如果不是稳定步行状态
		else {
			return mLastStep;// 返回上次的步数
		}

	}

	/**
	 * 把步数写入数据库
	 * 
	 * @param step
	 * @return
	 */
	private int writeStepToDB(int step) {

		// 如果当前时间==开始时间
		if (new Date(System.currentTimeMillis()).toString().equals(mDateStr)) {
			mDBHelper.updateData(mDB, step);// 更新步数
			return step;
		}

		// 如果当前时间!=开始时间
		else {
			mDBHelper.insertData(mDB, 0);// 插入新数据
			mDateStr = new Date(System.currentTimeMillis()).toString();// 重置日期
			mStepHistory = 0;// 重置历史值
			if (mStepDiff != -1) {
				mStepDiff = -1;// 重置步数差值
			}
			return 0;
		}
	}

}
