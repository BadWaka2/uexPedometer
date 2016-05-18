package org.zywx.wbpalmstar.plugin.uexpedometer.utils;

/**
 * 日期工具类
 * 
 * @author waka
 * @version createTime:2016年5月17日 下午5:52:11
 */
public class DateUtil {

	/**
	 * 检查日期格式
	 * 
	 * @param date
	 * @return
	 */
	public static boolean checkDateFormat(String date) {

		if (date.length() == 10) {
			return true;
		}
		return false;
	}

}
