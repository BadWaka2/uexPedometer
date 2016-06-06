package org.zywx.wbpalmstar.plugin.uexpedometer;

import java.lang.ref.WeakReference;

import org.json.JSONException;
import org.json.JSONObject;
import org.zywx.wbpalmstar.engine.EBrowserView;
import org.zywx.wbpalmstar.engine.universalex.EUExBase;
import org.zywx.wbpalmstar.engine.universalex.EUExCallback;
import org.zywx.wbpalmstar.plugin.uexpedometer.db.PedometerSQLiteHelper;
import org.zywx.wbpalmstar.plugin.uexpedometer.utils.DateUtil;
import org.zywx.wbpalmstar.plugin.uexpedometer.utils.MLog;

import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Message;

/**
 * 计步器入口类
 * 
 * @author waka
 *
 */
public class EUExPedometer extends EUExBase {

	// private static final String TAG = "EUExPedometer";

	// 回调
	private static final String CB_START_STEP_SERVICE = "uexPedometer.cbStartStepService";
	private static final String CB_STOP_STEP_SERVICE = "uexPedometer.cbStopStepService";
	private static final String CB_QUERY_STEP_TODAY = "uexPedometer.cbQueryStepToday";
	private static final String CB_QUERY_STEP_HISTORY = "uexPedometer.cbQueryStepHistory";

	/**
	 * 线程异步通信
	 */
	// handler
	private MyHandler mHandler = new MyHandler(this);
	// Message.what
	private static final int MSG_WHAT_QUERY_STEP_HISTORY = 4;

	/**
	 * 静态Handler内部类，避免内存泄漏
	 * 
	 * @author waka
	 *
	 */
	private static class MyHandler extends Handler {

		// 对Handler持有的对象使用弱引用
		private WeakReference<EUExPedometer> wrEUExPedometer;

		public MyHandler(EUExPedometer euExBaiduNavi) {
			wrEUExPedometer = new WeakReference<EUExPedometer>(euExBaiduNavi);
		}

		public void handleMessage(Message msg) {

			MLog.getIns().d("start");

			switch (msg.what) {
			case MSG_WHAT_QUERY_STEP_HISTORY:

				int totalStep = msg.arg1;
				MLog.getIns().i("totalStep = " + totalStep);
				try {
					JSONObject jsonObject = new JSONObject();
					jsonObject.put(JsConstant.FIELD_STATUS, JsConstant.VALUE_SUCCESS);
					jsonObject.put(JsConstant.FIELD_MESSAGE, "" + totalStep);
					wrEUExPedometer.get().jsCallback(CB_QUERY_STEP_HISTORY, 0, EUExCallback.F_C_TEXT, jsonObject.toString());
				} catch (JSONException e) {
					e.printStackTrace();
					MLog.getIns().e(e);
				}
				break;

			default:
				break;
			}
		}
	}

	/**
	 * 构造方法
	 * 
	 * @param arg0
	 * @param arg1
	 */
	public EUExPedometer(Context context, EBrowserView browserView) {
		super(context, browserView);

		// 创建数据库
		PedometerSQLiteHelper dbHelper = new PedometerSQLiteHelper(mContext, Constant.DB_NAME, null, 1);
		dbHelper.getWritableDatabase();// 得到数据库实例
	}

	/**
	 * clean
	 */
	@Override
	protected boolean clean() {
		return false;
	}

	/**
	 * 开始后台记步服务
	 * 
	 * @param parm
	 */
	public void startStepService(String[] parm) {
		Intent intent = new Intent(mContext, StepService.class);
		mContext.startService(intent);
		jsCallback(CB_START_STEP_SERVICE, 0, EUExCallback.F_C_TEXT, "StepService已被开启");
	}

	/**
	 * 关闭后台记步服务
	 * 
	 * @param parm
	 */
	public void stopStepService(String[] parm) {
		Intent intent = new Intent(mContext, StepService.class);
		mContext.stopService(intent);
		jsCallback(CB_STOP_STEP_SERVICE, 0, EUExCallback.F_C_TEXT, "StepService已被关闭");
	}

	/**
	 * 查询今天步数
	 * 
	 * @param parm
	 */
	public void queryStepToday(String[] parm) {

		String date = new java.sql.Date(System.currentTimeMillis()).toString();
		MLog.getIns().i("Today" + date);

		PedometerSQLiteHelper dbHelper = new PedometerSQLiteHelper(mContext, Constant.DB_NAME, null, Constant.DB_VERSION);
		SQLiteDatabase db = dbHelper.getWritableDatabase();// 得到数据库实例
		int step = dbHelper.queryStep(db, date);

		String jsonResult = "";
		if (step == -1) {
			cbErrorInfo(CB_QUERY_STEP_HISTORY, "查询失败，该日期不存在");
			return;
		}

		try {
			JSONObject jsonObject = new JSONObject();
			jsonObject.put(JsConstant.FIELD_STATUS, JsConstant.VALUE_SUCCESS);
			jsonObject.put(JsConstant.FIELD_MESSAGE, "" + step);
			jsonResult = jsonObject.toString();
		} catch (JSONException e) {
			e.printStackTrace();
			MLog.getIns().e(e);
		}
		jsCallback(CB_QUERY_STEP_TODAY, 0, EUExCallback.F_C_TEXT, jsonResult);

	}

	/**
	 * 查询历史步数
	 * 
	 * @param params
	 */
	public void queryStepHistory(String[] params) {

		MLog.getIns().d("start");

		if (params.length < 1) {
			MLog.getIns().e("parm.length < 1");
			return;
		}

		// 传一个参数，兼容以前的
		if (params.length == 1) {

			String date = params[0];

			if (!DateUtil.checkDateFormat(date)) {
				cbErrorInfo(CB_QUERY_STEP_HISTORY, "查询失败，请检查日期的格式");
				return;
			}

			PedometerSQLiteHelper dbHelper = new PedometerSQLiteHelper(mContext, "Pedometer.db", null, Constant.DB_VERSION);
			SQLiteDatabase db = dbHelper.getWritableDatabase();// 得到数据库实例
			int step = dbHelper.queryStep(db, date);

			if (step == -1) {
				cbErrorInfo(CB_QUERY_STEP_HISTORY, "查询失败，该日期不存在");
				return;
			}

			try {
				JSONObject jsonObject = new JSONObject();
				jsonObject.put(JsConstant.FIELD_STATUS, JsConstant.VALUE_SUCCESS);
				jsonObject.put(JsConstant.FIELD_MESSAGE, "" + step);
				jsCallback(CB_QUERY_STEP_HISTORY, 0, EUExCallback.F_C_TEXT, jsonObject.toString());
			} catch (JSONException e) {
				e.printStackTrace();
				MLog.getIns().e(e);
			}

		}

		// 传参大于等于2
		if (params.length >= 2) {

			final String date1 = params[0];
			final String date2 = params[1];
			MLog.getIns().i("date1 = " + date1 + " date2 = " + date2);
			if (!DateUtil.checkDateFormat(date1) || !DateUtil.checkDateFormat(date2)) {
				cbErrorInfo(CB_QUERY_STEP_HISTORY, "查询失败，请检查日期的格式");
				return;
			}

			final PedometerSQLiteHelper dbHelper = new PedometerSQLiteHelper(mContext, "Pedometer.db", null, Constant.DB_VERSION);
			final SQLiteDatabase db = dbHelper.getWritableDatabase();// 得到数据库实例
			new Thread() {
				public void run() {
					int totalStep = dbHelper.queryStep(db, date1, date2);
					Message msg = Message.obtain();
					msg.arg1 = totalStep;
					msg.what = MSG_WHAT_QUERY_STEP_HISTORY;
					mHandler.sendMessage(msg);
				};
			}.start();

		}
	}

	/**
	 * 回调给前端错误信息
	 * 
	 * @param cbMethod
	 * @param errorInfo
	 */
	private void cbErrorInfo(String cbMethod, String errorInfo) {
		try {
			JSONObject jsonObject = new JSONObject();
			jsonObject.put(JsConstant.FIELD_STATUS, JsConstant.VALUE_FAIL);
			jsonObject.put(JsConstant.FIELD_MESSAGE, errorInfo);
			jsCallback(cbMethod, 0, EUExCallback.F_C_TEXT, jsonObject.toString());
		} catch (JSONException e) {
			e.printStackTrace();
			MLog.getIns().e(e);
		}
	}

}
