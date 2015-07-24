package com.mapbox.mapboxsdk.tileprovider.tilesource;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.Log;

import com.mapbox.mapboxsdk.constants.MapboxConstants;
import com.mapbox.mapboxsdk.tileprovider.MapTile;
import com.mapbox.mapboxsdk.tileprovider.MapTileCache;
import com.mapbox.mapboxsdk.tileprovider.modules.MapTileDownloader;
import com.mapbox.mapboxsdk.util.NetworkUtils;
import com.mapbox.mapboxsdk.views.util.TileLoadedListener;
import com.mapbox.mapboxsdk.views.util.TilesLoadedListener;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import uk.co.senab.bitmapcache.CacheableBitmapDrawable;

/**
 * An implementation of {@link TileLayer} that pulls tiles from the internet.
 */
public class WebSourceTileLayer extends TileLayer implements MapboxConstants {
    private static final String TAG = "WebSourceTileLayer";

    // Tracks the number of threads active in the getBitmapFromURL method.
    private AtomicInteger activeThreads = new AtomicInteger(0);
    protected boolean mEnableSSL = false;

    public WebSourceTileLayer(final String pId, final String url) {
        this(pId, url, false);
    }

    public WebSourceTileLayer(final String pId, final String url, final boolean enableSSL) {
        super(pId, url);
        initialize(pId, url, enableSSL);
    }

    private boolean checkThreadControl() {
        return activeThreads.get() == 0;
    }

    @Override
    public TileLayer setURL(final String aUrl) {
        if (aUrl.contains(String.format(MAPBOX_LOCALE, "http%s://", (mEnableSSL ? "" : "s")))) {
            super.setURL(aUrl.replace(String.format(MAPBOX_LOCALE, "http%s://", (mEnableSSL ? "" : "s")),
                    String.format(MAPBOX_LOCALE, "http%s://", (mEnableSSL ? "s" : ""))));
        } else {
            super.setURL(aUrl);
        }
        return this;
    }

    protected void initialize(String pId, String aUrl, boolean enableSSL) {
        mEnableSSL = enableSSL;
        setURL(aUrl);
    }

    /**
     * Gets a list of Tile URLs used by this layer for a specific tile.
     *
     * @param aTile a map tile
     * @param hdpi a boolean that indicates whether the tile should be at 2x or retina size
     * @return a list of tile URLS
     */
    public String[] getTileURLs(final MapTile aTile, boolean hdpi) {
        String url = getTileURL(aTile, hdpi);
        if (!TextUtils.isEmpty(url)) {
            return new String[] { url };
        }
        return null;
    }

    /**
     * Get a single Tile URL for a single tile.
     *
     * @param aTile a map tile
     * @param hdpi a boolean that indicates whether the tile should be at 2x or retina size
     * @return a list of tile URLs
     */
    public String getTileURL(final MapTile aTile, boolean hdpi) {
        return parseUrlForTile(mUrl, aTile, hdpi);
    }

    protected String parseUrlForTile(String url, final MapTile aTile, boolean hdpi) {
        return url.replace("{z}", String.valueOf(aTile.getZ()))
                .replace("{x}", String.valueOf(aTile.getX()))
                .replace("{y}", String.valueOf(aTile.getY()))
                .replace("{2x}", hdpi ? "@2x" : "");
    }

    private static final Paint compositePaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    private Bitmap compositeBitmaps(final Bitmap source, Bitmap dest) {
        Canvas canvas = new Canvas(dest);
        canvas.drawBitmap(source, 0, 0, compositePaint);
        return dest;
    }

    @Override
    public CacheableBitmapDrawable getDrawableFromTile(final MapTileDownloader downloader,
            final MapTile aTile, boolean hdpi) {
        if (downloader.isNetworkAvailable()) {
            TilesLoadedListener listener = downloader.getTilesLoadedListener();

            boolean tempHDPI = hdpi;
            if (this instanceof MapboxTileLayer) {
                tempHDPI = false;
            }

            String[] urls = getTileURLs(aTile, tempHDPI);
            CacheableBitmapDrawable result = null;
            Bitmap resultBitmap = null;
            MapTileCache cache = downloader.getCache();

            if (urls != null) {
                if (listener != null) {
                    listener.onTilesLoadStarted();
                }

                resultBitmap = getComposedBitmapFromURLs(aTile, cache, urls);

                if (checkThreadControl()) {
                    if (listener != null) {
                        listener.onTilesLoaded();
                    }
                }
            }

            TileLoadedListener listener2 = downloader.getTileLoadedListener();
            if (listener2 != null) {
                //create the CacheableBitmapDrawable object from the bitmap
                result = cache.createCacheableBitmapDrawable(resultBitmap, aTile);

                //pass it to onTileLoaded callback for customization, and return the customized CacheableBitmapDrawable object
                result = listener2.onTileLoaded(result);

                //null pointer checking
                if (result != null) {
                    int resultWidth = result.getIntrinsicWidth();
                    int resultHeight = result.getIntrinsicHeight();

                    //convert the drawable updated in onTileLoaded callback to a bitmap
                    Bitmap bitmapToCache = Bitmap.createBitmap(resultWidth, resultHeight, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmapToCache);
                    result.setBounds(0, 0, resultWidth, resultHeight);
                    result.draw(canvas);

                    cache.putTileBitmap(aTile, bitmapToCache);
                }
            } else {
                if (resultBitmap != null) {
                    //get drawable by putting it into cache (memory and disk)
                    result = cache.putTileBitmap(aTile, resultBitmap);
                }
            }

            return result;
        }
        return null;
    }

    private Bitmap getComposedBitmapFromURLs(final MapTile mapTile, final MapTileCache cache, final String[] urls) {
        final CountDownLatch latch = new CountDownLatch(urls.length);
        final LinkedBlockingQueue<Bitmap> bitmaps = new LinkedBlockingQueue<>();
        activeThreads.incrementAndGet();
        try {
            for (final String url : urls) {
                try {
                    NetworkUtils.httpGet(new URL(url)).enqueue(new Callback() {
                        @Override
                        public void onFailure(Request request, IOException e) {
                            Log.e(TAG, "Error downloading MapTile: " + url + ":" + e);
                            latch.countDown();
                        }

                        @Override
                        public void onResponse(Response response) throws IOException {
                            try {
                                final Bitmap bitmap = BitmapFactory.decodeStream(response.body().byteStream());
                                if (bitmap != null) {
                                    cache.putTileInMemoryCache(mapTile, bitmap);
                                    bitmaps.offer(bitmap);
                                }
                            } finally {
                                latch.countDown();
                            }
                        }
                    });
                } catch (final Throwable e) {
                    Log.e(TAG, "Error starting MapTile download: " + url + ":" + e);
                }
            }
            latch.await();
        } catch(final InterruptedException e) {
            Log.e(TAG, "Interrupted while downloading MapTiles: " + Arrays.toString(urls) + ":" + e);
        } finally {
            activeThreads.decrementAndGet();
        }
        Bitmap result = bitmaps.poll();
        for (Bitmap bitmap : bitmaps) {
            compositeBitmaps(bitmap, result);
        }
        return result;
    }
}
