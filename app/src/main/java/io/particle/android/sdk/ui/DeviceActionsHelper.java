package io.particle.android.sdk.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.MenuItem;
import android.widget.PopupMenu;

import io.particle.android.sdk.cloud.ParticleDevice;
import io.particle.android.sdk.utils.ui.Toaster;
import io.particle.sdk.app.R;


public class DeviceActionsHelper {

    public static PopupMenu.OnMenuItemClickListener buildPopupMenuHelper(
            final FragmentActivity activity, final ParticleDevice device) {
        return new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                return takeActionForDevice(item.getItemId(), activity, device);
            }
        };
    }


    public static PopupMenu.OnMenuItemClickListener buildPopupMenuHelper(final Fragment fragment,
                                                                         final ParticleDevice device) {
        return buildPopupMenuHelper(fragment.getActivity(), device);
    }


    public static boolean takeActionForDevice(int actionId, FragmentActivity activity,
                                              ParticleDevice device) {
        switch (actionId) {
            case R.id.action_device_rename:
                RenameHelper.renameDevice(activity, device);
                return true;

            case R.id.action_device_unclaim:
                UnclaimHelper.unclaimDeviceWithDialog(activity, device);
                return true;

            case R.id.action_device_set_led_brightness:
                if(device.isConnected()) SetLedBrightnessHelper.setLedBrightness(activity, device);
                else Toaster.l(activity, "Device is offline");
                return true;

            default:
                return false;
        }
    }
}
