package io.particle.android.sdk.collect_reflect;

import android.app.Application;

import io.particle.android.sdk.devicesetup.ParticleDeviceSetupLibrary;
import io.particle.android.sdk.ui.DeviceListActivity;

public class CollectReflectApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ParticleDeviceSetupLibrary.init(this, DeviceListActivity.class);
    }
}
