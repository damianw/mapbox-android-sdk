/**
 * @author Brad Leege <bleege@gmail.com>
 * Created on 2/15/14 at 3:26 PM
 */

package com.mapbox.mapboxsdk.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.squareup.okhttp.CacheControl;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;

import java.net.URL;

public class NetworkUtils {

    public static final String USER_AGENT = MapboxUtils.getUserAgent();

    private static OkHttpClient client = new OkHttpClient();

    public static OkHttpClient getClient() {
        return client;
    }

    public static void setClient(OkHttpClient client) {
        if (client != null) {
            NetworkUtils.client = client.clone(); // shallow copy
        }
    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    public static Call httpGet(final URL url) {
        return httpGet(url, null);
    }

    public static Call httpGet(final URL url, final CacheControl cacheControl) {
        final Request.Builder builder = new Request.Builder()
          .addHeader("User-Agent", USER_AGENT)
          .get()
          .url(url);
        if (cacheControl != null) { builder.cacheControl(cacheControl); }
        return client.newCall(builder.build());
    }

}
