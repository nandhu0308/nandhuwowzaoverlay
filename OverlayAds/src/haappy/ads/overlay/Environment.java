package haappy.ads.overlay;

public final class Environment {

	static Boolean envMode = null;

	public static boolean isDebugMode() {
		if (envMode == null) {
			String mode = System.getenv("WOWZA_DEVELOPER_MODE");
			envMode = mode != null && mode.equalsIgnoreCase("DEBUG");
		}
		return envMode;
	}

}
