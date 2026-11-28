package com.foxwidget.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.foxwidget.app.widget.FoxWidgetProvider

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        FoxMonitorService.start(context)
        FoxWidgetProvider.updateAllWidgets(context)
    }
}
