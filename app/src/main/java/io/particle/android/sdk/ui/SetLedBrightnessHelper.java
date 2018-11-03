package io.particle.android.sdk.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import io.particle.android.sdk.cloud.ParticleCloudException;
import io.particle.android.sdk.cloud.ParticleDevice;
import io.particle.android.sdk.utils.Async;
import io.particle.android.sdk.utils.CoreNameGenerator;
import io.particle.android.sdk.utils.ui.Toaster;
import io.particle.sdk.app.R;

import static io.particle.android.sdk.utils.Py.list;

/**
 * Created by rjure on 4/2/2016.
 */
public class SetLedBrightnessHelper {

    public static void setLedBrightness(FragmentActivity activity, ParticleDevice device) {
        new SetLedBrightnessHelper(device, activity).showDialog();
    }

    private final ParticleDevice device;
    private final FragmentActivity activity;

    private SetLedBrightnessHelper(ParticleDevice device, FragmentActivity activity) {
        this.device = device;
        this.activity = activity;
    }

    private void showDialog() {
        new MaterialDialog.Builder(activity)
                .title("Set LED brightness")
                .theme(Theme.LIGHT)
                .items(R.array.led_brightness_array)
                .itemsCallbackSingleChoice(-1, new MaterialDialog.ListCallbackSingleChoice() {
                    @Override
                    public boolean onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
                        return true;
                    }
                })
                .positiveText("Set")
                .negativeText("Cancel")
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        Integer selected = dialog.getSelectedIndex();
                        switch(selected){
                            case 0:
                                doSetLedBrightness("255");
                                break;
                            case 1:
                                doSetLedBrightness("50");
                                break;
                            case 2:
                                doSetLedBrightness("1");
                                break;
                            case 3:
                                doSetLedBrightness("0");
                                break;
                            default:
                                // Do nothing
                                break;
                        }
                    }
                })
                .show();
    }

    private void doSetLedBrightness(final String myBrightness){
        Async.executeAsync(device, new Async.ApiWork<ParticleDevice, Integer>() {
            int resultCode;

            @Override
            public Integer callApi(@NonNull ParticleDevice particleDevice)
                    throws ParticleCloudException, IOException {
                try {
                    resultCode = particleDevice.callFunction("setup", list("led " + myBrightness));
                } catch (IOException | ParticleCloudException | ParticleDevice.FunctionDoesNotExistException error) {
                    return null;
                }
                return resultCode;
            }

            @Override
            public void onSuccess(@Nullable Integer value) {
                // Horray! do nothing..
            }

            @Override
            public void onFailure(@NonNull ParticleCloudException e) {
                Log.e("SetLedBrightnes", "Cannot set specified brightness", e);
            }
        });
    }


}
