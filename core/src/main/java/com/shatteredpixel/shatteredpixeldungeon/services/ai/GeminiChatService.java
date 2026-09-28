/*
 * Shattered Pixel Dungeon - Ghost AI Chat Addon
 *
 * This file was added to hook the Ghost NPC up to the Google Gemini API,
 * so the ghost can hold a free-form, in-character conversation with the
 * player instead of only offering its scripted quest lines.
 */

package com.shatteredpixel.shatteredpixeldungeon.services.ai;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.watabou.noosa.Game;
import com.watabou.utils.Callback;

import java.util.List;

public class GeminiChatService {

	//NOTE: this key is embedded directly in the app. That's fine for your own
	//personal build, but never share an APK built like this with anyone else,
	//since they'd be able to extract the key and use up your quota.
	private static final String API_KEY = "__GEMINI_KEY__";

	private static final String MODEL = "gemini-2.5-flash";
	private static final String ENDPOINT =
			"https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent";

	//The ghost's personality. Edit this text to change how it talks.
	private static final String SYSTEM_PROMPT =
			"You are the ghost of a dead adventurer, haunting a dungeon in the game " +
			"Shattered Pixel Dungeon. You are talking directly to a hero exploring the " +
			"dungeon. Stay in character at all times: melancholic, a little cryptic, " +
			"but not hostile. Keep every reply short - at most 2 or 3 short sentences. " +
			"Never mention that you are an AI, a language model, or Gemini.";

	public interface ChatCallback {
		void onResponse(String replyText);
		void onError(String errorMessage);
	}

	//One line of conversation history, used to give the model context.
	public static class Turn {
		public final boolean fromPlayer;
		public final String text;
		public Turn(boolean fromPlayer, String text) {
			this.fromPlayer = fromPlayer;
			this.text = text;
		}
	}

	//history should end with the newest player message.
	public static void sendMessage(List<Turn> history, final ChatCallback callback) {

		String body;
		try {
			body = buildRequestBody(history);
		} catch (Exception e) {
			callback.onError("Could not build request.");
			return;
		}

		Net.HttpRequest req = new Net.HttpRequest(Net.HttpMethods.POST);
		req.setUrl(ENDPOINT);
		req.setHeader("Content-Type", "application/json");
		req.setHeader("x-goog-api-key", API_KEY);
		req.setContent(body);

		Gdx.net.sendHttpRequest(req, new Net.HttpResponseListener() {
			@Override
			public void handleHttpResponse(Net.HttpResponse httpResponse) {
				final int status = httpResponse.getStatus().getStatusCode();
				final String result = httpResponse.getResultAsString();
				Game.runOnRenderThread(new Callback() {
					@Override
					public void call() {
						if (status < 200 || status >= 300) {
							callback.onError("Server returned status " + status + ".");
							return;
						}
						try {
							callback.onResponse(parseReply(result));
						} catch (Exception e) {
							callback.onError("Could not read the response.");
						}
					}
				});
			}

			@Override
			public void failed(Throwable t) {
				Game.runOnRenderThread(new Callback() {
					@Override
					public void call() {
						callback.onError("Connection failed. Check your internet connection.");
					}
				});
			}

			@Override
			public void cancelled() {
				Game.runOnRenderThread(new Callback() {
					@Override
					public void call() {
						callback.onError("Request cancelled.");
					}
				});
			}
		});
	}

	private static String buildRequestBody(List<Turn> history) {
		JsonValue root = new JsonValue(JsonValue.ValueType.object);

		JsonValue systemInstruction = new JsonValue(JsonValue.ValueType.object);
		JsonValue sysParts = new JsonValue(JsonValue.ValueType.array);
		JsonValue sysPart = new JsonValue(JsonValue.ValueType.object);
		sysPart.addChild("text", new JsonValue(SYSTEM_PROMPT));
		sysParts.addChild(sysPart);
		systemInstruction.addChild("parts", sysParts);
		root.addChild("system_instruction", systemInstruction);

		JsonValue contents = new JsonValue(JsonValue.ValueType.array);
		for (Turn t : history) {
			JsonValue entry = new JsonValue(JsonValue.ValueType.object);
			entry.addChild("role", new JsonValue(t.fromPlayer ? "user" : "model"));
			JsonValue parts = new JsonValue(JsonValue.ValueType.array);
			JsonValue part = new JsonValue(JsonValue.ValueType.object);
			part.addChild("text", new JsonValue(t.text));
			parts.addChild(part);
			entry.addChild("parts", parts);
			contents.addChild(entry);
		}
		root.addChild("contents", contents);

		JsonValue generationConfig = new JsonValue(JsonValue.ValueType.object);
		generationConfig.addChild("maxOutputTokens", new JsonValue(200));
		root.addChild("generationConfig", generationConfig);

		return root.toJson(JsonWriter.OutputType.json);
	}

	private static String parseReply(String responseBody) {
		JsonValue parsed = new JsonReader().parse(responseBody);
		JsonValue candidates = parsed.get("candidates");
		JsonValue firstCandidate = candidates.get(0);
		JsonValue parts = firstCandidate.get("content").get("parts");
		String text = parts.get(0).getString("text");
		return text.trim();
	}
}
