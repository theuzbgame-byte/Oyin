/*
 * Shattered Pixel Dungeon - Ghost AI Chat Addon
 *
 * A simple chat window: shows the ghost's last line, lets the player type a
 * reply, and sends the whole exchange to GeminiChatService.
 */

package com.shatteredpixel.shatteredpixeldungeon.windows;

import com.shatteredpixel.shatteredpixeldungeon.Chrome;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.services.ai.GeminiChatService;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.watabou.noosa.TextInput;

import java.util.ArrayList;
import java.util.List;

public class WndGhostChat extends Window {

	private static final int WIDTH_P = 130;
	private static final int WIDTH_L = 200;
	private static final int MARGIN = 2;
	private static final int BUTTON_HEIGHT = 16;
	private static final int INPUT_HEIGHT = 16;

	private final int width;

	private RenderedTextBlock replyBlock;
	private TextInput textBox;
	private RedButton sendBtn;

	private boolean waitingForReply = false;

	private final List<GeminiChatService.Turn> history = new ArrayList<>();

	public WndGhostChat() {
		super();

		width = PixelScene.landscape() ? WIDTH_L : WIDTH_P;

		replyBlock = PixelScene.renderTextBlock(
				"*The ghost drifts closer, and seems willing to talk...*", 6);
		replyBlock.maxWidth(width);
		replyBlock.setPos(0, 0);
		add(replyBlock);

		textBox = new TextInput(Chrome.get(Chrome.Type.TOAST_WHITE), false, (int)(PixelScene.uiCamera.zoom*9)) {
			@Override
			public void enterPressed() {
				send();
			}
		};
		add(textBox);

		sendBtn = new RedButton("Send"){
			@Override
			protected void onClick() {
				send();
			}
		};
		add(sendBtn);

		layout();
	}

	private void layout() {
		float pos = replyBlock.bottom() + 2*MARGIN;

		textBox.setRect(MARGIN, pos, width - 2*MARGIN, INPUT_HEIGHT);
		pos = textBox.bottom() + MARGIN;

		sendBtn.setRect(MARGIN, pos, width - 2*MARGIN, BUTTON_HEIGHT);
		pos = sendBtn.bottom();

		resize(width, (int)pos);
	}

	private void send() {
		if (waitingForReply) return;

		String msg = textBox.getText();
		if (msg == null || msg.trim().isEmpty()) return;

		history.add(new GeminiChatService.Turn(true, msg));
		textBox.setText("");

		replyBlock.text("*The ghost seems to be listening...*", width);
		layout();

		waitingForReply = true;
		sendBtn.enable(false);

		GeminiChatService.sendMessage(new ArrayList<>(history), new GeminiChatService.ChatCallback() {
			@Override
			public void onResponse(String replyText) {
				history.add(new GeminiChatService.Turn(false, replyText));
				replyBlock.text(replyText, width);
				layout();
				waitingForReply = false;
				sendBtn.enable(true);
			}

			@Override
			public void onError(String errorMessage) {
				replyBlock.text("*The ghost's voice fades away.* (" + errorMessage + ")", width);
				layout();
				waitingForReply = false;
				sendBtn.enable(true);
			}
		});
	}
}
