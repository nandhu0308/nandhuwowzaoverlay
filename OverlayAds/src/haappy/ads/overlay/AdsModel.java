package haappy.ads.overlay;

import java.util.List;

public class AdsModel {

	private int id, logoAdId, adEventId;
	private String timeSlotStart, timeSlotEnd, adPlacement, adTarget, geoXCoordinate, geoYCoordinate, logoFtpPath,
			streamSource, lowerText, adType;
	private AdType eventAdType = AdType.NONE;

	// LOGO , L-BAND,BOTTOM-BAR,VIDEO,SLIDE
	public String getAdType() {
		String result = "LOGO_TOP_RIGHT";
		if (adType != null && !adType.isEmpty()) {
			if (adType.equalsIgnoreCase("LOGO")) {
				if (adPlacement != null && !adPlacement.isEmpty() && !adPlacement.equalsIgnoreCase("NONE")) {
					result = (adType.trim() + "_" + adPlacement.trim()).toUpperCase();
				}

			} else {
				result = adType.toUpperCase();
			}
		}

		return result;
	}

	public void setAdType(String adType) {
		this.adType = adType;
	}

	public AdType getEventAdType() {
		if (eventAdType == AdType.NONE && !getAdType().isEmpty())
			eventAdType = AdType.valueOf(getAdType());
		return eventAdType;
	}

	public String getStreamSource() {
		return streamSource;
	}

	public void setStreamSource(String streamSource) {
		this.streamSource = streamSource;
	}

	public String getLowerText() {
		return lowerText;
	}

	public void setLowerText(String lowerText) {
		this.lowerText = lowerText;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getLogoAdId() {
		return logoAdId;
	}

	public void setLogoAdId(int logoAdId) {
		this.logoAdId = logoAdId;
	}

	public int getAdEventId() {
		return adEventId;
	}

	public void setAdEventId(int adEventId) {
		this.adEventId = adEventId;
	}

	public String getTimeSlotStart() {
		return timeSlotStart;
	}

	public void setTimeSlotStart(String timeSlotStart) {
		this.timeSlotStart = timeSlotStart;
	}

	public String getTimeSlotEnd() {
		return timeSlotEnd;
	}

	public void setTimeSlotEnd(String timeSlotEnd) {
		this.timeSlotEnd = timeSlotEnd;
	}

	public String getAdPlacement() {
		return adPlacement;
	}

	public void setAdPlacement(String adPlacement) {
		this.adPlacement = adPlacement;
	}

	public String getAdTarget() {
		return adTarget != null ? adTarget.toLowerCase() : "";
	}

	public void setAdTarget(String adTarget) {
		this.adTarget = adTarget;
	}

	public String getGeoXCoordinate() {
		return geoXCoordinate;
	}

	public void setGeoXCoordinate(String geoXCoordinate) {
		this.geoXCoordinate = geoXCoordinate;
	}

	public String getGeoYCoordinate() {
		return geoYCoordinate;
	}

	public void setGeoYCoordinate(String geoYCoordinate) {
		this.geoYCoordinate = geoYCoordinate;
	}

	public String getLogoFtpPath() {
		return logoFtpPath;
	}

	public void setLogoFtpPath(String logoFtpPath) {
		this.logoFtpPath = logoFtpPath;
	}

	public StreamTarget getStreamTarget() {
		return StreamTarget.valueOf(getAdTarget());
	}

	public String getHashMapKey() {
		return StreamManager.getInstance().createHashMapKey(getStreamTarget(), getEventAdType());
	}

	public int getOverlayIndex() {
		return AdType.getOverlayIndex(getEventAdType());
	}

}
