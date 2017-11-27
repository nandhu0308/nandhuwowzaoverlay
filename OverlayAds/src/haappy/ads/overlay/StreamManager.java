package haappy.ads.overlay;

import java.util.HashMap;

public class StreamManager {

	static StreamManager _instance;

	public static StreamManager getInstance() {
		if (_instance == null) {
			_instance = new StreamManager();
		}
		return _instance;
	}

	HashMap<String, StreamTarget> streamNameTargetMap = new HashMap<>();

	private StreamManager() {
		streamNameTargetMap.put("youtube_source", StreamTarget.youtube);
		streamNameTargetMap.put("facebook_source", StreamTarget.facebook);
		streamNameTargetMap.put("periscope_source", StreamTarget.periscope);
		streamNameTargetMap.put("website_source", StreamTarget.website);
		streamNameTargetMap.put("android_source", StreamTarget.android);
		streamNameTargetMap.put("ios_source", StreamTarget.ios);

	}

	public StreamTarget getStreamTarget(String streamName) {
		if (streamNameTargetMap.containsKey(streamName))
			return streamNameTargetMap.get(streamName);
		else
			return StreamTarget.None;
	}
	
	public  String createHashMapKey(StreamTarget target, AdType adType) {
		return target.toString() + adType.toString();
	}

	

}
