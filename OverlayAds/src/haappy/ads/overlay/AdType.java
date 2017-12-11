package haappy.ads.overlay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum AdType {
	NONE, LOGO_TOP_LEFT, LOGO_TOP_CENTER, LOGO_TOP_RIGHT, LOGO_MIDDLE_LEFT, LOGO_MIDDLE_CENTER, LOGO_MIDDLE_RIGHT, LOGO_BOTTOM_LEFT, LOGO_BOTTOM_CENTER, LOGO_BOTTOM_RIGHT, L_BAND, BOTTOM_BAR, VIDEO, SLIDE;

	public static int getOverlayIndex(AdType adType) {
		int result;
		switch (adType) {
		case LOGO_TOP_LEFT:
			result = 1;
			break;
		case LOGO_TOP_CENTER:
			result = 2;
			break;
		case LOGO_TOP_RIGHT:
			result = 3;
			break;

		case LOGO_MIDDLE_LEFT:
			result = 4;
			break;
		case LOGO_MIDDLE_CENTER:
			result = 5;
			break;
		case LOGO_MIDDLE_RIGHT:
			result = 6;
			break;

		case LOGO_BOTTOM_LEFT:
			result = 7;
			break;
		case LOGO_BOTTOM_CENTER:
			result = 8;
			break;
		case LOGO_BOTTOM_RIGHT:
			result = 9;
			break;

		case BOTTOM_BAR:
			result = 10;
			break;
		case L_BAND:
			result = 11;
			break;
		case SLIDE:
			result = 12;
			break;
		case VIDEO:
			result = 13;
			break;
		default:
			result = 0;
			break;
		}
		return result;
	}

	public static List<AdType> getavailableTypes() {
		return Arrays.asList(LOGO_TOP_LEFT, LOGO_TOP_CENTER, LOGO_TOP_RIGHT, LOGO_MIDDLE_LEFT, LOGO_MIDDLE_CENTER,
				LOGO_MIDDLE_RIGHT, LOGO_BOTTOM_LEFT, LOGO_BOTTOM_CENTER, LOGO_BOTTOM_RIGHT, BOTTOM_BAR, L_BAND);
	}

}
