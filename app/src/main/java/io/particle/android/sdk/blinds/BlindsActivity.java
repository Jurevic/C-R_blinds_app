package io.particle.android.sdk.blinds;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import io.particle.android.sdk.blinds.BlindsActivityFragment;
import io.particle.android.sdk.cloud.ParticleDevice;
import io.particle.android.sdk.ui.BaseActivity;
import io.particle.android.sdk.ui.DeviceListActivity;
import io.particle.sdk.app.R;

/**
 * This activity is mostly just a 'shell' activity containing nothing
 * more than a {@link BlindsActivityFragment}.
 */
public class BlindsActivity extends BaseActivity {


    public static Intent buildIntent(Context ctx, ParticleDevice device) {
        return new Intent(ctx, BlindsActivity.class)
                .putExtra(BlindsActivityFragment.ARG_DEVICE, device);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.blinds_menu, menu);
        return true;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_detail);

        // Show the Up button in the action bar.
        ActionBar supportActionBar = getSupportActionBar();
        if(supportActionBar == null) return;

        supportActionBar.setDisplayHomeAsUpEnabled(true);

        // FIXME: do this with a theme attr instead.
        ColorDrawable color = new ColorDrawable(getResources().getColor(
                R.color.shaded_background));
        supportActionBar.setBackgroundDrawable(color);


        // savedInstanceState is non-null when there is fragment state
        // saved from previous configurations of this activity
        // (e.g. when rotating the screen from portrait to landscape).
        // In this case, the fragment will automatically be re-added
        // to its container so we don't need to manually add it.
        // For more information, see the Fragments API guide at:
        //
        // http://developer.android.com/guide/components/fragments.html
        //
        if (savedInstanceState == null) {
            // Create the detail fragment and add it to the activity
            // using a fragment transaction.
            ParticleDevice device = getIntent().getParcelableExtra(BlindsActivityFragment.ARG_DEVICE);
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.device_detail_container, BlindsActivityFragment.newInstance(device))
                    .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            // This ID represents the Home or Up button. In the case of this
            // activity, the Up button is shown. Use NavUtils to allow users
            // to navigate up one level in the application structure. For
            // more details, see the Navigation pattern on Android Design:
            //
            // http://developer.android.com/design/patterns/navigation.html#up-vs-back
            //
            NavUtils.navigateUpTo(this, new Intent(this, DeviceListActivity.class));
            return true;
        }
        else if(id == R.id.setupBlindsMenuItem){
            //startActivity(BlindsActivity.buildIntent(this, device));
        }
        return super.onOptionsItemSelected(item);
    }
}
