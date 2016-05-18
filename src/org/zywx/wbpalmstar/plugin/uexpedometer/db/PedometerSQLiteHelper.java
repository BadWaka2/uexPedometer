package org.zywx.wbpalmstar.plugin.uexpedometer.db;

import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.zywx.wbpalmstar.plugin.uexpedometer.Constant;
import org.zywx.wbpalmstar.plugin.uexpedometer.utils.MLog;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * 数据库操作帮助类，实现了插入，查询，更新功能，为追求客观真实，删除功能未写
 * 
 * @author waka
 *
 */
public class PedometerSQLiteHelper extends SQLiteOpenHelper {

	/**
	 * 构造方法
	 */
	public PedometerSQLiteHelper(Context context, String name, CursorFactory factory, int version) {
		super(context, name, factory, version);
	}

	/**
	 * 创建数据库
	 */
	@Override
	public void onCreate(SQLiteDatabase db) {

		MLog.getIns().d("start");

		// @formatter:off
		String sql = "CREATE TABLE " + Constant.TABLE_NAME_PEDOMETER + " ("
				+ Constant.FIELD_NAME_DATE + " DATE PRIMARY KEY,"
				+ Constant.FIELD_NAME_STEP + " INTEGER"
				+ ") ";
		// @formatter:on

		db.execSQL(sql);// 创建步数表
		MLog.getIns().i("创建数据表成功");
	}

	/**
	 * 升级数据库
	 */
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

		MLog.getIns().d("start");

		db.execSQL("drop table if exists " + Constant.TABLE_NAME_PEDOMETER);
		onCreate(db);

	}

	/**
	 * 根据日期查询数据库中某一天的步数
	 * 
	 * @param db
	 * @param date
	 * @return 如果有数据，则返回数据；没有数据返回-1
	 */
	public synchronized int queryStep(SQLiteDatabase db, String date) {
		int step = -1;
		Cursor cursor = db.rawQuery("SELECT * FROM " + Constant.TABLE_NAME_PEDOMETER + " WHERE " + Constant.FIELD_NAME_DATE + " = ?", new String[] { date });
		if (cursor.moveToFirst()) {
			step = cursor.getInt(cursor.getColumnIndex(Constant.FIELD_NAME_STEP));
		}
		cursor.close();// 记住用完了要释放掉
		return step;
	}

	/**
	 * 查询两个日期之间的步数总和
	 * 
	 * 因为要查询数据库的次数很多，建议放在线程里执行
	 * 
	 * @param db
	 * @param sDate
	 * @param eDate
	 * @return
	 */
	public synchronized int queryStep(SQLiteDatabase db, String sDate, String eDate) {

		MLog.getIns().d("start");

		// 总步数
		int totalStep = 0;

		// startTime
		String[] sDates = sDate.split("-");
		int sYear = Integer.valueOf(sDates[0]);
		int sMonth = Integer.valueOf(sDates[1]);
		int sDay = Integer.valueOf(sDates[2]);
		Calendar start = Calendar.getInstance();
		start.set(sYear, sMonth - 1, sDay);// 月份减一是因为月份是从0开始代表一月的
		Long startTime = start.getTimeInMillis();
		MLog.getIns().i("startTime = " + startTime);

		// endTime
		String[] eDates = eDate.split("-");
		int eYear = Integer.valueOf(eDates[0]);
		int eMonth = Integer.valueOf(eDates[1]);
		int eDay = Integer.valueOf(eDates[2]);
		Calendar end = Calendar.getInstance();
		end.set(eYear, eMonth - 1, eDay);// 月份减一是因为月份是从0开始代表一月的
		Long endTime = end.getTimeInMillis();

		Long oneDay = 1000 * 60 * 60 * 24l;
		endTime = endTime - oneDay;// 输入2016-05-17,2016-05-18，返回的数据应该是只有17号的步数，所以需要对结束时间减去一天
		MLog.getIns().i("endTime = " + endTime);

		Cursor cursor = null;
		Long time = startTime;
		while (time < endTime) {
			Date date = new Date(time);
			DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
			String dateStr = df.format(date);
			MLog.getIns().i(dateStr);

			cursor = db.rawQuery("SELECT * FROM " + Constant.TABLE_NAME_PEDOMETER + " WHERE " + Constant.FIELD_NAME_DATE + " = ?", new String[] { dateStr });
			if (cursor.moveToFirst()) {
				totalStep += cursor.getInt(cursor.getColumnIndex(Constant.FIELD_NAME_STEP));
			}
			time += oneDay;
			MLog.getIns().i("time = " + time + " endTime = " + endTime);
		}
		cursor.close();
		return totalStep;
	}

	/**
	 * 向数据库中插入数据，只能插入今天的数据;因为要开启多个进程保证service不被kill掉的几率，所以改动数据库的操作需加锁
	 * 
	 * @param db
	 * @param step
	 * @return 插入成功返回true，失败返回false
	 */
	public synchronized boolean insertData(SQLiteDatabase db, int step) {

		// 判断数据库中是否有今天日期的数据
		int isExsit = queryStep(db, new Date(System.currentTimeMillis()).toString());
		if (isExsit == -1) {// 如果没有今天的数据，则可插入，返回true
			ContentValues values = new ContentValues();
			values.put(Constant.FIELD_NAME_DATE, new Date(System.currentTimeMillis()).toString());
			values.put(Constant.FIELD_NAME_STEP, step);
			db.insert(Constant.TABLE_NAME_PEDOMETER, null, values);// 插入数据
			return true;
		} else {// 如果有今天的数据，插入失败，返回false
			MLog.getIns().i(new Date(System.currentTimeMillis()).toString() + "的数据已存在！不可重复插入");
			return false;
		}
	}

	/**
	 * 更新数据库中的数据，只能更新今天的数据;因为要开启多个进程保证service不被kill掉的几率，所以改动数据库的操作需加锁
	 * 
	 * @param db
	 * @param step
	 * @return 更新成功返回true，失败返回false
	 */
	public synchronized boolean updateData(SQLiteDatabase db, int step) {

		// 判断数据库中是否有今天日期的数据
		int isExsit = queryStep(db, new Date(System.currentTimeMillis()).toString());
		if (isExsit == -1) {// 如果没有今天的数据，则不可更新，返回false
			MLog.getIns().i(new Date(System.currentTimeMillis()).toString() + "的数据不存在！不能更新");
			return false;
		} else {
			db.execSQL("UPDATE " + Constant.TABLE_NAME_PEDOMETER + " SET " + Constant.FIELD_NAME_STEP + " = " + step + " WHERE _date = '" + new Date(System.currentTimeMillis()).toString() + "'");
			return true;
		}
	}

}
