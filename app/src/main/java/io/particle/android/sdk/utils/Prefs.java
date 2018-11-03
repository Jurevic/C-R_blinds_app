package io.particle.android.sdk.utils;

import android.content.Context;
import android.content.SharedPreferences;






public class Prefs {

    private static final String BUCKET_NAME = "particleAppPrefsBucket";

    private static final String KEY_COMPLETED_FIRST_LOGIN = "completedFirstLogin";
    private static final String KEY_CORES_JSON_ARRAY = "coresJsonArray";



    private static Prefs instance = null;


    public static Prefs getInstance(Context ctx) {
        if (instance == null) {
            instance = new Prefs(ctx);
        }

        return instance;
    }


    private final SharedPreferences prefs;


    private Prefs(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(BUCKET_NAME, Context.MODE_PRIVATE);
    }

    public boolean getCompletedFirstLogin() {
        return prefs.getBoolean(KEY_COMPLETED_FIRST_LOGIN, false);
    }

    public void saveCompletedFirstLogin(boolean value) {
        prefs.edit().putBoolean(KEY_COMPLETED_FIRST_LOGIN, value).apply();
    }






    public void clear() {
        boolean completed = getCompletedFirstLogin();
        prefs.edit().clear().apply();
        saveCompletedFirstLogin(completed);
    }

    private void saveString(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    private void applyString(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

}