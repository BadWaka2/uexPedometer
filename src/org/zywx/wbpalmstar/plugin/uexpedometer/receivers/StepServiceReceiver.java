package org.zywx.wbpalmstar.plugin.uexpedometer.receivers;

import org.zywx.wbpalmstar.plugin.uexpedometer.EUExPedometer;
import org.zywx.wbpalmstar.plugin.uexpedometer.utils.MLog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 计步服务广播接收器，用来与StepService进行进程间通信
 * 
 * @author waka
 * @version createTime:2016年6月6日 上午10:50:42
 */
public class StepServiceReceiver extends BroadcastReceiver {

	@SuppressWarnings("unused")
	private EUExPedometer mEUExPedometer;

	public StepServiceReceiver(EUExPedometer uexPedometer) {
		mEUExPedometer = uexPedometer;
	}

	@Override
	public void onReceive(Context context, Intent intent) {

		String action = intent.getAction();

		if (action == null) {
			MLog.getIns().e("action == null");
			return;
		}

	}

}
