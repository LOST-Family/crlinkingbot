package crlinkingbot.util;

import org.json.JSONObject;

/**
 * Liest die Antwort der Link-API des CR-Managers aus. Der meldet Fehler im Feld
 * "error" (nicht "message") und lässt "playerName" weg, wenn die CR-API den
 * Namen gerade nicht liefert – beides darf keine Antwort hängen lassen.
 */
public final class LinkAntwort {

	private LinkAntwort() {
	}

	/** Grund eines Fehlschlags auf Deutsch, oder null, wenn keiner bekannt ist. */
	public static String fehlergrund(JSONObject result) {
		String roh = null;
		final JSONObject data = result.optJSONObject("data");
		if (data != null) {
			roh = data.optString("error", null);
			if (roh == null && !data.optBoolean("success", false)) {
				roh = data.optString("message", null);
			}
			if ("Player already linked to another user".equals(roh)) {
				final String wem = data.optString("linkedUserId", null);
				return "Dieser Spieler-Tag ist bereits verknüpft" + (wem != null ? " (mit <@" + wem + ">)." : ".");
			}
		}
		if (roh == null) {
			roh = result.optString("error", null);
		}
		if (roh == null) {
			roh = result.optString("message", null);
		}
		if (roh == null || roh.isBlank()) {
			return null;
		}
		if ("Player not found or API error".equals(roh)) {
			return "Spieler-Tag nicht gefunden (Tippfehler?) oder die Clash-Royale-API antwortet gerade nicht.";
		}
		return roh;
	}

	/** Spielername aus der Erfolgsantwort; fehlt er, der Tag. */
	public static String spielerName(JSONObject result, String eingegebenerTag) {
		final JSONObject data = result.optJSONObject("data");
		final String name = data != null ? data.optString("playerName", null) : null;
		return name != null && !name.isBlank() ? name : tag(result, eingegebenerTag);
	}

	/** Der Tag, wie der CR-Manager ihn gespeichert hat (groß, mit #, O als 0). */
	public static String tag(JSONObject result, String eingegebenerTag) {
		final JSONObject data = result.optJSONObject("data");
		final String tag = data != null ? data.optString("tag", null) : null;
		return tag != null && !tag.isBlank() ? tag : eingegebenerTag;
	}
}
