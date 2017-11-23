package haappy.ads.overlay;

import java.lang.reflect.Type;

import org.json.JSONException;
import org.json.JSONObject;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class GsonHelper {
	 private final static String TAG = "ERROR";

	    @SuppressWarnings("unchecked")
		public static <T> T parseJSON(String jsonString, Class type) {
	        Gson gson = new Gson();
	        return (T) gson.fromJson(jsonString, type);
	    }

	    public static <T> JSONObject getJson(T object) {
	        Gson gson = new Gson();
	        Type typeToken = new TypeToken<T>() {
	        }.getType();
	        JSONObject result = null;
	        try {
	            result = new JSONObject(gson.toJson(object, typeToken));
	        } catch (JSONException e) {
	            result = null;
	            
	        }
	        return result;

	    }
}
