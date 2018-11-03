package io.particle.android.sdk.blinds;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import io.particle.sdk.app.R;

/**
 * A placeholder fragment containing a simple view.
 */
public class BlindsSetupFragment extends Fragment {

    public BlindsSetupFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_blinds_setup, container, false);
    }
}
