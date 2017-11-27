package haappy.ads.overlay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum AdType {
	NONE, LOGO, L_BAND, BOTTOM_BAR, VIDEO, SLIDE;
	public static int getOverlayIndex(AdType adType) {
		int result;
		switch (adType) {
		case LOGO:
			result = 1;
			break;
		case BOTTOM_BAR:
			result = 2;
			break;
		case L_BAND:
			result = 3;
			break;
		case SLIDE:
			result = 4;
			break;
		case VIDEO:
			result = 5;
			break;
		default:
			result = 0;
			break;
		}
		return result;
	}

	public static List<AdType> getavailableTypes() {
		return Arrays.asList(LOGO, BOTTOM_BAR);
	}

}
