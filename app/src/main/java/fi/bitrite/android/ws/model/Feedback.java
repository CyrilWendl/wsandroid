package fi.bitrite.android.ws.model;

import android.os.Parcel;

import com.yelp.parcelgen.JsonParser.DualCreator;

import org.json.JSONException;
import org.json.JSONObject;


public class Feedback extends _Feedback implements Comparable<Feedback> {

    public static final DualCreator<Feedback> CREATOR = new DualCreator<Feedback>() {

        public Feedback[] newArray(int size) {
            return new Feedback[size];
        }

        public Feedback createFromParcel(Parcel source) {
            Feedback object = new Feedback();
            object.readFromParcel(source);
            return object;
        }

        @Override
        public Feedback parse(JSONObject obj) throws JSONException {
            Feedback newInstance = new Feedback();
            newInstance.readFromJson(obj);
            return newInstance;
        }
    };

    @Override
    public int compareTo(Feedback other) {
        return (int) (other.getHostingDate().compareTo(this.getHostingDate()));
    }
}
