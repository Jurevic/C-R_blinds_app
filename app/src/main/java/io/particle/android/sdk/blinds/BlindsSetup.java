package io.particle.android.sdk.blinds;

import android.os.Bundle;
import android.app.Activity;

import io.particle.sdk.app.R;

public class BlindsSetup extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blinds_setup);
        getActionBar().setDisplayHomeAsUpEnabled(true);
    }

}
