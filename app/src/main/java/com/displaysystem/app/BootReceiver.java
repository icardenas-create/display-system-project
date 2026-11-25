package com.displaysystem.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Opens the slideshow automatically when the TV finishes starting up. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent start = new Intent(context, MainActivity.class);
        start.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(start);
        } catch (RuntimeException ignored) {
            // Android blocked the start; the app can still be opened from the home screen.
        }
    }
}
