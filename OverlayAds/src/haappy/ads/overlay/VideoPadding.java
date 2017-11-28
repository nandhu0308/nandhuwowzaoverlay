package haappy.ads.overlay;

public class VideoPadding {
	private int left, top, right, bottom;

	public int getLeft(double scalingFactor) {
		return (int) (left * scalingFactor);
	}

	public void setLeft(int left) {
		this.left = left;
	}

	public int getTop(double scalingFactor) {
		return (int) (top * scalingFactor);
	}

	public void setTop(int top) {
		this.top = top;
	}

	public int getRight(double scalingFactor) {
		return (int) (right * scalingFactor);
	}

	public void setRight(int right) {
		this.right = right;
	}

	public int getBottom(double scalingFactor) {
		return (int) (bottom * scalingFactor);
	}

	public void setBottom(int bottom) {
		this.bottom = bottom;
	}

	public static VideoPadding getVideoPadding(AdType adType, int width, int height) {
		VideoPadding padding = new VideoPadding();
		switch (adType) {
		case L_BAND:
			padding.setLeft((int)( (200.0 / 1280.0) * width));
			padding.setBottom((int)( (150.0 / 720.0) * height));
			break;
		default:
			break;
		}

		return padding;
	}

}
