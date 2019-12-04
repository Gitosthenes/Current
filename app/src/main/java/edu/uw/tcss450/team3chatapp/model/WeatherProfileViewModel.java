package edu.uw.tcss450.team3chatapp.model;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import edu.uw.tcss450.team3chatapp.R;
import edu.uw.tcss450.team3chatapp.utils.GetAsyncTask;
import edu.uw.tcss450.team3chatapp.utils.Utils;

public class WeatherProfileViewModel extends AndroidViewModel {

    private static WeatherProfileViewModel mInstance;
    private MutableLiveData<WeatherProfile> mCurrentLocationWeatherProfile;
    private MutableLiveData<List<WeatherProfile>> mSavedLocationsWeatherProfiles;
    private long mLastUpdated;
    private ArrayList<Location> mSavedLocations;

    private WeatherProfileViewModel(Application theApp) {
        super(theApp);
        mCurrentLocationWeatherProfile = new MutableLiveData<>();
        mSavedLocationsWeatherProfiles = new MutableLiveData<>();
        mLastUpdated = System.currentTimeMillis() / 1000L;
    }

    // Private setters
    private void setCurrentLocationWeatherProfile(final WeatherProfile theWP) {mCurrentLocationWeatherProfile.setValue(theWP);}

    private void setSavedLocationWeatherProfile(final ArrayList<WeatherProfile> theWPs) {mSavedLocationsWeatherProfiles.setValue(theWPs);}

    private void setTimeStamp(final long theTime) {mLastUpdated = theTime;}

    public LiveData<WeatherProfile> getCurrentLocationWeatherProfile() {
        return mCurrentLocationWeatherProfile;
    }

    public LiveData<List<WeatherProfile>> getSavedLocationWeatherProfiles() {
        return mSavedLocationsWeatherProfiles;
    }

    public long getTimeStamp() {
        return mLastUpdated;
    }

    public void update(ArrayList<Location> theLocationsToUpdate) {
        if(theLocationsToUpdate.size() > 0) {
            mSavedLocations = theLocationsToUpdate;

            Uri uri = new Uri.Builder()
                    .scheme("https")
                    .authority("team3-chatapp-backend.herokuapp.com")
                    .appendPath("weather")
                    .appendPath("batch")
                    .appendQueryParameter("requests", buildWeatherQuery(theLocationsToUpdate))
                    .build();

            new GetAsyncTask.Builder(uri.toString())
                    .onPostExecute(this::fetchWeatherPost)
                    .onCancelled(error -> Log.e("", error))
                    .build().execute();
        } else {
            Log.d("WEATHER_ERR", "Unable to get device location & no saved locations");
        }
    }

    private void fetchWeatherPost(final String result) {
        try {
            JSONObject root = new JSONObject(result).getJSONObject("response");
            if(root.has("responses")) {
                JSONArray data = root.getJSONArray("responses");
                ArrayList<WeatherProfile> savedLocationWeatherProfileList = new ArrayList<>();

                for(int i = 0; i < data.length(); i+=3) {
                    int id = i / 3;
                    Location loc = mSavedLocations.get(id);
                    String obsJSONStr = data.getJSONObject(i).toString();
                    String dailyJSONStr = data.getJSONObject(i+1).toString();
                    String hourlyJSONStr = data.getJSONObject(i+2).toString();
                    String cityState = getCityState(obsJSONStr);

                    WeatherProfile wp = new WeatherProfile(loc, obsJSONStr, dailyJSONStr, hourlyJSONStr, cityState);

                    // First block of weather info is always current location
                    if(i == 0) {
                        mCurrentLocationWeatherProfile.setValue(wp);
                    } else {
                        savedLocationWeatherProfileList.add(wp);
                    }
                }
                mSavedLocationsWeatherProfiles.setValue(savedLocationWeatherProfileList);
                mLastUpdated = System.currentTimeMillis() / 1000L;

                // Save updated WeatherProfileVM info to sharedPrefs:
                SharedPreferences prefs = getApplication().getSharedPreferences(getApplication().getString(R.string.keys_shared_prefs), Context.MODE_PRIVATE);
                Gson gson = new Gson();
                prefs.edit().putString(getApplication().getString(R.string.keys_prefs_weathervm_current), gson.toJson(getCurrentLocationWeatherProfile().getValue())).apply();
                prefs.edit().putString(getApplication().getString(R.string.keys_prefs_weathervm_saved), gson.toJson(getSavedLocationWeatherProfiles().getValue())).apply();
                prefs.edit().putLong(getApplication().getString(R.string.keys_prefs_weathervm_lastupdated), mLastUpdated).apply();

            }
        } catch(JSONException e) {
            e.printStackTrace();
            Log.e("WEATHER_UPDATE_ERR", Objects.requireNonNull(e.getMessage()));
        }
    }

    private String buildWeatherQuery(ArrayList<Location> tSavedLocations) {
        StringBuilder result = new StringBuilder();

        String qm = "%3F";
        String amp = "%26";

        for(Location loc : tSavedLocations) {
            StringBuilder req = new StringBuilder();
            String locEP = loc.getLatitude() + "," + loc.getLongitude();

            //append current weather request: '/observations/{locEP}?fields={obsFields},'
            req.append("/observations/").append(locEP).append(qm)
                    .append("fields=").append(Utils.OBS_FIELDS)
                    .append(",");
            //append 10-day forecast request: '/forecasts/{locEP}?limit=10&fields={dailyFields},'
            req.append("/forecasts/").append(locEP).append(qm)
                    .append("limit=10").append(amp)
                    .append("fields=").append(Utils.DAILY_FIELDS)
                    .append(",");
            //append 24hr forecast request: '/forecasts/{locEP}?filter=1hr&limit=24&fields={hourlyFields},'
            req.append("/forecasts/").append(locEP).append(qm)
                    .append("filter=1hr").append(amp)
                    .append("limit=24").append(amp)
                    .append("fields=").append(Utils.HOURLY_FIELDS)
                    .append(",");

            //append query string for this location to entire request.
            result.append(req);
        }
        //remove trailing comma:
        result.deleteCharAt(result.length()-1);

        return result.toString();
    }

    private String getCityState(final String theJSONasStr) {

        String result = "";

        try {
            JSONObject theJSON = new JSONObject(theJSONasStr);
            if (theJSON.has(getApplication().getString(R.string.keys_json_weather_response))) {
                JSONObject response = theJSON.getJSONObject(getApplication().getString(R.string.keys_json_weather_response));
                if(response.has(getApplication().getString(R.string.keys_json_weather_place))) {

                    JSONObject place = response.getJSONObject(getApplication().getString(R.string.keys_json_weather_place));

                    result = Utils.formatCityState(place.getString(getApplication().getString(R.string.keys_json_weather_name)),
                            place.getString(getApplication().getString(R.string.keys_json_weather_state)).toUpperCase());

                } else {
                    Log.d("WEATHER_POST", "Either Place or Ob missing form Response: " + response.toString());
                }
            }
        } catch(JSONException e){e.printStackTrace();}

        return result;
    }

    public static class WeatherFactory extends ViewModelProvider.NewInstanceFactory {
        private final Application mApplication;

        public WeatherFactory(Application theApplication) {
            mApplication = theApplication;
        }

        @SuppressWarnings("unchecked")
        @NonNull
        @Override
        public WeatherProfileViewModel create(@NonNull Class modelClass) {
            if(mInstance == null) {
                mInstance = new WeatherProfileViewModel(mApplication);
                SharedPreferences prefs =  mApplication.getSharedPreferences(mApplication.getString(R.string.keys_shared_prefs), Context.MODE_PRIVATE);
                if(prefs.contains(mApplication.getString(R.string.keys_prefs_weathervm_current))
                        &&prefs.contains(mApplication.getString(R.string.keys_prefs_weathervm_saved))
                        &&prefs.contains(mApplication.getString(R.string.keys_prefs_weathervm_lastupdated))) {

                    //setup Gson and types
                    Gson gson = new Gson();
                    Type typeCurrent = new TypeToken<WeatherProfile>(){}.getType();
                    Type typeSaved = new TypeToken<List<WeatherProfile>>(){}.getType();

                    // Get current WP, saved location WPs & last updated from SharedPrefs and convert using Gson
                    String currentWPasString = prefs.getString(mApplication.getString(R.string.keys_prefs_weathervm_current), "");
                    WeatherProfile currentWP = gson.fromJson(currentWPasString, typeCurrent);

                    String savedWPsAsString = prefs.getString(mApplication.getString(R.string.keys_prefs_weathervm_saved), "");
                    ArrayList<WeatherProfile> savedWPs = gson.fromJson(savedWPsAsString, typeSaved);

                    long lastUpdated = prefs.getLong(mApplication.getString(R.string.keys_prefs_weathervm_lastupdated), 0);

                    // Set new instance's fields to what we pulled from SharedPrefs
                    mInstance.setCurrentLocationWeatherProfile(currentWP);
                    mInstance.setSavedLocationWeatherProfile(savedWPs);
                    mInstance.setTimeStamp(lastUpdated);
                }
            }
            return mInstance;
        }
    }
}