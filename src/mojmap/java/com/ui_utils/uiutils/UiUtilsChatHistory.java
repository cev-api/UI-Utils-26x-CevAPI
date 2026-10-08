package com.ui_utils.uiutils;

import java.util.List;

/** Per-field history cursor; the current draft is kept beyond the newest message. */
final class UiUtilsChatHistory {
	private int offset;
	private String draft = "";

	String move(List<String> sent, String current, int direction) {
		if (offset == 0) draft = current;
		offset = Math.max(0, Math.min(sent.size(), offset - direction));
		return offset == 0 ? draft : sent.get(sent.size() - offset);
	}

	void reset() {
		offset = 0;
		draft = "";
	}
}
