package haappy.ads.overlay;

import com.sun.corba.se.impl.corba.NVListImpl;

public class ApiManager {

	static ApiManager _instance;

	private ApiManager() {
		baseUrl = productionBaseUrl;
		if (Environment.isDebugMode()) {
			baseUrl = debugBaseUrl;
		}

	}

	public static ApiManager getInstance() {
		if (_instance == null) {
			_instance = new ApiManager();
		}
		return _instance;
	}

	private final String debugBaseUrl = "http://localhost:8080/LLCWeb/engage/";
	private final String productionBaseUrl = "https://services.beinglimitless.in/engage/";
	private String baseUrl = productionBaseUrl;

	public String getChannelIdApi() {
		return baseUrl + "broadcast/application/channel";
	}

	public String getEventAdsApi(int eventId) {
		return baseUrl + "ads/get/logo/event/" + eventId;
	}

	public String getChannelEventApi(String channelId) {
		return baseUrl + "ads/get/event/channel/" + channelId;
	}

}
