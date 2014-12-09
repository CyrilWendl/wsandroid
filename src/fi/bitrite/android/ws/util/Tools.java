package fi.bitrite.android.ws.util;

import android.content.Context;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.Html;
import android.text.Spanned;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.android.gms.maps.model.LatLng;

import fi.bitrite.android.ws.R;
import fi.bitrite.android.ws.WSAndroidApplication;

/**
 * General simple tools, mostly public methods.
 */
public class Tools {

    // Convert text ("About me" == Comments from user data) to form to add to TextView
    public static Spanned siteHtmlToHtml(String text) {
        return Html.fromHtml(text.replace("\n", "<br/>"));
    }

    /**
     * Return distance between two points in km/miles
     *
     * @param l1
     * @param l2
     * @param units (mi or km)
     * @return
     */
    static public int calculateDistanceBetween(Location l1, Location l2, String units) {
        double factor = units.equals("mi") ? 1609.34 : 1000;
        float meters = l1.distanceTo(l2);
        return (int) (meters / factor);
    }

    static public int calculateDistanceBetween(LatLng l1, Location l2, String units) {
        Location location = new Location("fromlatlng");
        location.setLatitude(l1.latitude);
        location.setLongitude(l1.longitude);
        return calculateDistanceBetween(location, l2, units);
    }

    static public boolean isNetworkConnected(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
        boolean simulateDisconnected = context.getResources().getBoolean(R.integer.simulate_network_disconnected);

        if (simulateDisconnected) {
            return false;
        }
        return isConnected;
    }

    /**
     * Send a report to Google Analytics about  category/action
     *
     * @param context
     * @param category
     * @param action
     */
    static public void gaReportException(Context context, String category, String action) {

        Tracker exceptionTracker = ((WSAndroidApplication) context.getApplicationContext())
                .getTracker(WSAndroidApplication.TrackerName.APP_TRACKER);

        exceptionTracker.send(new HitBuilders.EventBuilder()
                        .setCategory(category)
                        .setAction(action)
                        .build()
        );
    }

}
