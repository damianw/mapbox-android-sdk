/**
 * @author Brad Leege <bleege@gmail.com>
 * Created on 2/15/14 at 3:26 PM
 */

package com.mapbox.mapboxsdk.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.squareup.okhttp.Cache;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import javax.net.ssl.SSLSocketFactory;

public class NetworkUtils {

    public static final String USER_AGENT = MapboxUtils.getUserAgent();

    private static OkHttpClient primaryClient = new OkHttpClient();
    private static OkHttpClient secondaryClient = new OkHttpClient();

    public static OkHttpClient getClient() {
        return primaryClient;
    }

    public static void setClient(OkHttpClient client) {
        if (client != null) {
            primaryClient = client.clone(); // shallow copy
            secondaryClient = client.clone();
        }
    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    public static Call httpGet(final URL url) {
        return httpGet(url, null, null);
    }

    public static Call httpGet(final URL url, final Cache cache) {
        return httpGet(url, cache, null);
    }

    public static Call httpGet(final URL url, final Cache cache, final SSLSocketFactory sslSocketFactory) {
        final OkHttpClient client;
        if (cache != null) {
            client = secondaryClient;
            client.setCache(cache);
            if (sslSocketFactory != null) {
                client.setSslSocketFactory(sslSocketFactory);
            }
        } else {
            client = primaryClient;
        }
        final Request request = new Request.Builder().addHeader("User-Agent", USER_AGENT)
          .get()
          .url(url)
          .build();
        return client.newCall(request);
    }

    public static Cache getCache(final File cacheDir, final int maxSize) throws IOException {
        return new Cache(cacheDir, maxSize);
    }
}
