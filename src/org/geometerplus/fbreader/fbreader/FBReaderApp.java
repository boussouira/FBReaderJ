/*
 * Copyright (C) 2007-2013 Geometer Plus <contact@geometerplus.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.fbreader.fbreader;

import java.util.*;

import org.geometerplus.zlibrary.core.application.*;
import org.geometerplus.zlibrary.core.filesystem.ZLFile;
import org.geometerplus.zlibrary.core.filetypes.*;
import org.geometerplus.zlibrary.core.library.ZLibrary;
import org.geometerplus.zlibrary.core.options.*;
import org.geometerplus.zlibrary.core.resources.ZLResource;
import org.geometerplus.zlibrary.core.util.*;

import org.geometerplus.zlibrary.text.hyphenation.ZLTextHyphenator;
import org.geometerplus.zlibrary.text.model.ZLTextModel;
import org.geometerplus.zlibrary.text.view.*;
import org.geometerplus.zlibrary.text.view.style.ZLTextStyleCollection;

import org.geometerplus.fbreader.book.*;
import org.geometerplus.fbreader.bookmodel.*;
import org.geometerplus.fbreader.formats.*;
import org.geometerplus.fbreader.fbreader.options.*;

public final class FBReaderApp extends ZLApplication {
	public static final ZLTextStyleCollection TextStyleCollection;

	public static final ZLBooleanOption YotaDrawOnBackScreenOption;
	public static final ZLBooleanOption AllowScreenBrightnessAdjustmentOption;
	public static final ZLStringOption TextSearchPatternOption;

	public static final ZLBooleanOption EnableDoubleTapOption;
	public static final ZLBooleanOption NavigateAllWordsOption;

	public static enum WordTappingAction {
		doNothing, selectSingleWord, startSelecting, openDictionary
	}
	public static final ZLEnumOption<WordTappingAction> WordTappingActionOption;

	public static final ZLColorOption ImageViewBackgroundOption;
	public static final ZLEnumOption<FBView.ImageFitting> FitImagesToScreenOption;
	public static enum ImageTappingAction {
		doNothing, selectImage, openImageView
	}
	public static final ZLEnumOption<ImageTappingAction> ImageTappingActionOption;
	public static final ZLBooleanOption ImageMatchBackgroundOption;

	public static final ViewOptions ViewOptions;

	public static final ZLIntegerRangeOption ScrollbarTypeOption;

	public static final PageTurningOptions PageTurningOptions;
	public static final FooterOptions FooterOptions;

	private static final ZLKeyBindings ourBindings;

	static {
		TextStyleCollection = new ZLTextStyleCollection("Base");

		YotaDrawOnBackScreenOption =
			new ZLBooleanOption("LookNFeel", "YotaDrawOnBack", false);
		AllowScreenBrightnessAdjustmentOption =
			new ZLBooleanOption("LookNFeel", "AllowScreenBrightnessAdjustment", true);
		TextSearchPatternOption =
			new ZLStringOption("TextSearch", "Pattern", "");

		EnableDoubleTapOption =
			new ZLBooleanOption("Options", "EnableDoubleTap", false);
		NavigateAllWordsOption =
			new ZLBooleanOption("Options", "NavigateAllWords", false);

		WordTappingActionOption =
			new ZLEnumOption<WordTappingAction>("Options", "WordTappingAction", WordTappingAction.startSelecting);

		ImageViewBackgroundOption =
			new ZLColorOption("Colors", "ImageViewBackground", new ZLColor(255, 255, 255));
		FitImagesToScreenOption =
			new ZLEnumOption<FBView.ImageFitting>("Options", "FitImagesToScreen", FBView.ImageFitting.covers);
		ImageTappingActionOption =
			new ZLEnumOption<ImageTappingAction>("Options", "ImageTappingAction", ImageTappingAction.openImageView);
		ImageMatchBackgroundOption =
			new ZLBooleanOption("Colors", "ImageMatchBackground", true);

		ViewOptions = new ViewOptions();

		ScrollbarTypeOption =
			new ZLIntegerRangeOption("Options", "ScrollbarType", 0, 3, FBView.SCROLLBAR_SHOW_AS_FOOTER);
		PageTurningOptions = new PageTurningOptions();
		FooterOptions = new FooterOptions();

		ourBindings = ZLKeyBindings.get("Keys");
	}

	public final FBView BookTextView;
	public final FBView FootnoteView;
	private String myFootnoteModelId;

	public volatile BookModel Model;

	private ZLTextPosition myJumpEndPosition;
	private Date myJumpTimeStamp;

	public final IBookCollection Collection;

	public FBReaderApp(IBookCollection collection) {
		Collection = collection;

		collection.addListener(new IBookCollection.Listener() {
			public void onBookEvent(BookEvent event, Book book) {
				switch (event) {
					case BookmarkStyleChanged:
					case BookmarksUpdated:
						if (Model != null && (book == null || book.equals(Model.Book))) {
							if (BookTextView.getModel() != null) {
								setBookmarkHighlightings(BookTextView, null);
							}
							if (FootnoteView.getModel() != null && myFootnoteModelId != null) {
								setBookmarkHighlightings(FootnoteView, myFootnoteModelId);
							}
						}
						break;
					case Updated:
						onBookUpdated(book);
						break;
				}
			}

			public void onBuildEvent(IBookCollection.Status status) {
			}
		});

		addAction(ActionCode.INCREASE_FONT, new ChangeFontSizeAction(this, +2));
		addAction(ActionCode.DECREASE_FONT, new ChangeFontSizeAction(this, -2));

		addAction(ActionCode.FIND_NEXT, new FindNextAction(this));
		addAction(ActionCode.FIND_PREVIOUS, new FindPreviousAction(this));
		addAction(ActionCode.CLEAR_FIND_RESULTS, new ClearFindResultsAction(this));

		addAction(ActionCode.SELECTION_CLEAR, new SelectionClearAction(this));

		addAction(ActionCode.TURN_PAGE_FORWARD, new TurnPageAction(this, true));
		addAction(ActionCode.TURN_PAGE_BACK, new TurnPageAction(this, false));

		addAction(ActionCode.MOVE_CURSOR_UP, new MoveCursorAction(this, FBView.Direction.up));
		addAction(ActionCode.MOVE_CURSOR_DOWN, new MoveCursorAction(this, FBView.Direction.down));
		addAction(ActionCode.MOVE_CURSOR_LEFT, new MoveCursorAction(this, FBView.Direction.rightToLeft));
		addAction(ActionCode.MOVE_CURSOR_RIGHT, new MoveCursorAction(this, FBView.Direction.leftToRight));

		addAction(ActionCode.VOLUME_KEY_SCROLL_FORWARD, new VolumeKeyTurnPageAction(this, true));
		addAction(ActionCode.VOLUME_KEY_SCROLL_BACK, new VolumeKeyTurnPageAction(this, false));

		addAction(ActionCode.SWITCH_TO_DAY_PROFILE, new SwitchProfileAction(this, ColorProfile.DAY));
		addAction(ActionCode.SWITCH_TO_NIGHT_PROFILE, new SwitchProfileAction(this, ColorProfile.NIGHT));

		addAction(ActionCode.EXIT, new ExitAction(this));

		BookTextView = new FBView(this);
		FootnoteView = new FBView(this);

		setView(BookTextView);
	}

	public void openBook(final Book book, final Bookmark bookmark, final Runnable postAction) {
		System.err.println("openbook");
		if (Model != null && Model.isValid()) {
			System.err.println("1");
			if (book == null || bookmark == null && book.File.getPath().equals(Model.Book.File.getPath())) {
				return;
			}
		}
		System.err.println("2");
		Book tempBook = book;
		if (tempBook == null) {
			System.err.println("3");
			tempBook = Collection.getRecentBook(0);
			if (tempBook == null || !tempBook.File.exists()) {
				tempBook = Collection.getBookByFile(BookUtil.getHelpFile());
			}
			if (tempBook == null) {
				return;
			}
		}
		System.err.println("4");
		final Book bookToOpen = tempBook;
		bookToOpen.addLabel(Book.READ_LABEL);
		Collection.saveBook(bookToOpen);
		final FormatPlugin p = PluginCollection.Instance().getPlugin(bookToOpen.File);
		if (p == null) return;
		if (p.type() == FormatPlugin.Type.EXTERNAL) {
			System.err.println("5");
			runWithMessage("extract", new Runnable() {
				public void run() {
					final ZLFile f = ((ExternalFormatPlugin)p).prepareFile(bookToOpen.File);
					if (myExternalFileOpener.openFile(f, Formats.filetypeOption(FileTypeCollection.Instance.typeForFile(bookToOpen.File).Id).getValue())) {
						Collection.addBookToRecentList(bookToOpen);
						closeWindow();
					} else {
						openBook(null, null, null);
					}
				}
			}, postAction);
			return;
		}
		if (p.type() == FormatPlugin.Type.PLUGIN) {
			System.err.println("6");
			BookTextView.setModel(null);
			FootnoteView.setModel(null);
			clearTextCaches();
			Model = BookModel.createPluginModel(bookToOpen);
			final Bookmark bm;
			if (bookmark != null) {
				bm = bookmark;
			} else {
				ZLTextPosition pos = Collection.getStoredPosition(bookToOpen.getId());
				if (pos == null) {
					pos = new ZLTextFixedPosition(0, 0, 0);
				}
				bm = new Bookmark(bookToOpen, "", pos, pos, "", false);
			}
			runWithMessage("loadingBook", new Runnable() {
				public void run() {
					final ZLFile f = ((PluginFormatPlugin)p).prepareFile(bookToOpen.File);
					System.err.println(SerializerUtil.serialize(bm));
					myPluginFileOpener.openFile(((PluginFormatPlugin)p).getPackage(), SerializerUtil.serialize(bm), SerializerUtil.serialize(bookToOpen));
				}
			}, postAction);
			return;
		}
		if (Model != null && !Model.isValid()) {
			Model = null;
		}
		runWithMessage("loadingBook", new Runnable() {
			public void run() {
				openBookInternal(bookToOpen, bookmark, false);
			}
		}, postAction);
	}

	public void reloadBook() {
		if (Model != null && Model.Book != null) {
			runWithMessage("loadingBook", new Runnable() {
				public void run() {
					openBookInternal(Model.Book, null, true);
				}
			}, null);
		}
	}


	public static ColorProfile getColorProfile() {
		return ColorProfile.get(getColorProfileName());
	}

	public static String getColorProfileName() {
		return new ZLStringOption("Options", "ColorProfile", ColorProfile.DAY).getValue();
	}

	public void setColorProfileName(String name) {
		new ZLStringOption("Options", "ColorProfile", ColorProfile.DAY).setValue(name);
	}

	public ZLKeyBindings keyBindings() {
		return ourBindings;
	}

	public static ZLKeyBindings keyBindingsStatic() {
		return ourBindings;
	}

	public final static boolean hasActionForKeyStatic(int key, boolean longPress) {
		final String actionId = keyBindingsStatic().getBinding(key, longPress);
		return actionId != null && !NoAction.equals(actionId);
	}

	public FBView getTextView() {
		return (FBView)getCurrentView();
	}

	public void tryOpenFootnote(String id) {
		if (Model != null) {
			myJumpEndPosition = null;
			myJumpTimeStamp = null;
			BookModel.Label label = Model.getLabel(id);
			if (label != null) {
				if (label.ModelId == null) {
					if (getTextView() == BookTextView) {
						addInvisibleBookmark();
						myJumpEndPosition = new ZLTextFixedPosition(label.ParagraphIndex, 0, 0);
						myJumpTimeStamp = new Date();
					}
					BookTextView.gotoPosition(label.ParagraphIndex, 0, 0);
					setView(BookTextView);
				} else {
					setFootnoteModel(label.ModelId);
					setView(FootnoteView);
					FootnoteView.gotoPosition(label.ParagraphIndex, 0, 0);
				}
				getViewWidget().repaint();
			}
		}
	}

	public void clearTextCaches() {
		BookTextView.clearCaches();
		FootnoteView.clearCaches();
	}

	public Bookmark addSelectionBookmark() {
		final FBView fbView = getTextView();
		final String text = fbView.getSelectedText();

		final Bookmark bookmark = new Bookmark(
			Model.Book,
			fbView.getModel().getId(),
			fbView.getSelectionStartPosition(),
			fbView.getSelectionEndPosition(),
			text,
			true
		);
		Collection.saveBookmark(bookmark);
		fbView.clearSelection();

		return bookmark;
	}

	private void setBookmarkHighlightings(ZLTextView view, String modelId) {
		view.removeHighlightings(BookmarkHighlighting.class);
		for (BookmarkQuery query = new BookmarkQuery(Model.Book, 20); ; query = query.next()) {
			final List<Bookmark> bookmarks = Collection.bookmarks(query);
			if (bookmarks.isEmpty()) {
				break;
			}
			for (Bookmark b : bookmarks) {
				if (b.getEnd() == null) {
					b.findEnd(view);
				}
				if (MiscUtil.equals(modelId, b.ModelId)) {
					view.addHighlighting(new BookmarkHighlighting(view, Collection, b));
				}
			}
		}
	}

	private void setFootnoteModel(String modelId) {
		final ZLTextModel model = Model.getFootnoteModel(modelId);
		FootnoteView.setModel(model);
		if (model != null) {
			myFootnoteModelId = modelId;
			setBookmarkHighlightings(FootnoteView, modelId);
		}
	}

	synchronized void openBookInternal(Book book, Bookmark bookmark, boolean force) {
		if (Model != null && book.File.getPath().equals(Model.Book.File.getPath())) {
			if (bookmark != null) {
				gotoBookmark(bookmark, false);
				return;
			} else if (!force) {
				return;
			}
		}

		if (!force && Model != null && book.equals(Model.Book)) {
			if (bookmark != null) {
				gotoBookmark(bookmark, false);
			}
			return;
		}

		onViewChanged();

		storePosition();
		BookTextView.setModel(null);
		FootnoteView.setModel(null);
		clearTextCaches();

		Model = null;
		System.gc();
		System.gc();
		try {
			Model = BookModel.createModel(book);
			Collection.saveBook(book);
			ZLTextHyphenator.Instance().load(book.getLanguage());
			BookTextView.setModel(Model.getTextModel());
			setBookmarkHighlightings(BookTextView, null);
			BookTextView.gotoPosition(Collection.getStoredPosition(book.getId()));
			if (bookmark == null) {
				setView(BookTextView);
			} else {
				gotoBookmark(bookmark, false);
			}
			Collection.addBookToRecentList(book);
			final StringBuilder title = new StringBuilder(book.getTitle());
			if (!book.authors().isEmpty()) {
				boolean first = true;
				for (Author a : book.authors()) {
					title.append(first ? " (" : ", ");
					title.append(a.DisplayName);
					first = false;
				}
				title.append(")");
			}
			setTitle(title.toString());
		} catch (BookReadingException e) {
			processException(e);
		}

		getViewWidget().reset();
		getViewWidget().repaint();
	}

	private List<Bookmark> invisibleBookmarks() {
		final List<Bookmark> bookmarks = Collection.bookmarks(
			new BookmarkQuery(Model.Book, false, 10)
		);
		Collections.sort(bookmarks, new Bookmark.ByTimeComparator());
		return bookmarks;
	}

	public boolean jumpBack() {
		try {
			if (getTextView() != BookTextView) {
				showBookTextView();
				return true;
			}

			if (myJumpEndPosition == null || myJumpTimeStamp == null) {
				return false;
			}
			// more than 2 minutes ago
			if (myJumpTimeStamp.getTime() + 2 * 60 * 1000 < new Date().getTime()) {
				return false;
			}
			if (!myJumpEndPosition.equals(BookTextView.getStartCursor())) {
				return false;
			}

			final List<Bookmark> bookmarks = invisibleBookmarks();
			if (bookmarks.isEmpty()) {
				return false;
			}
			final Bookmark b = bookmarks.get(0);
			Collection.deleteBookmark(b);
			gotoBookmark(b, true);
			return true;
		} finally {
			myJumpEndPosition = null;
			myJumpTimeStamp = null;
		}
	}

	private void gotoBookmark(Bookmark bookmark, boolean exactly) {
		final String modelId = bookmark.ModelId;
		if (modelId == null) {
			addInvisibleBookmark();
			if (exactly) {
				BookTextView.gotoPosition(bookmark);
			} else {
				BookTextView.gotoHighlighting(
					new BookmarkHighlighting(BookTextView, Collection, bookmark)
				);
			}
			setView(BookTextView);
		} else {
			setFootnoteModel(modelId);
			if (exactly) {
				FootnoteView.gotoPosition(bookmark);
			} else {
				FootnoteView.gotoHighlighting(
					new BookmarkHighlighting(FootnoteView, Collection, bookmark)
				);
			}
			setView(FootnoteView);
		}
		getViewWidget().repaint();
	}

	public void showBookTextView() {
		setView(BookTextView);
	}

	public void onWindowClosing() {
		storePosition();
	}

	public void storePosition() {
		if (Model != null && Model.isValid() && Model.Book != null && BookTextView != null) {
			Collection.storePosition(Model.Book.getId(), BookTextView.getStartCursor());
			Model.Book.setProgress(BookTextView.getProgress());
			Collection.saveBook(Model.Book);
		}
	}

	public boolean hasCancelActions() {
		return new CancelMenuHelper().getActionsList(Collection).size() > 1;
	}

	public void runCancelAction(CancelMenuHelper.ActionType type, Bookmark bookmark) {
		switch (type) {
			case library:
				runAction(ActionCode.SHOW_LIBRARY);
				break;
			case networkLibrary:
				runAction(ActionCode.SHOW_NETWORK_LIBRARY);
				break;
			case previousBook:
				openBook(Collection.getRecentBook(1), null, null);
				break;
			case returnTo:
				Collection.deleteBookmark(bookmark);
				gotoBookmark(bookmark, true);
				break;
			case close:
				closeWindow();
				break;
		}
	}

	private synchronized void updateInvisibleBookmarksList(Bookmark b) {
		if (Model != null && Model.Book != null && b != null) {
			for (Bookmark bm : invisibleBookmarks()) {
				if (b.equals(bm)) {
					Collection.deleteBookmark(bm);
				}
			}
			Collection.saveBookmark(b);
			final List<Bookmark> bookmarks = invisibleBookmarks();
			for (int i = 3; i < bookmarks.size(); ++i) {
				Collection.deleteBookmark(bookmarks.get(i));
			}
		}
	}

	public void addInvisibleBookmark(ZLTextWordCursor cursor) {
		if (cursor != null && Model != null && Model.Book != null && Model.isValid() && getTextView() == BookTextView) {
			updateInvisibleBookmarksList(Bookmark.createBookmark(
				Model.Book,
				getTextView().getModel().getId(),
				cursor,
				6,
				false
			));
		}
	}

	public void addInvisibleBookmark() {
		if (Model.Book != null && getTextView() == BookTextView) {
			updateInvisibleBookmarksList(createBookmark(6, false));
		}
	}

	public Bookmark createBookmark(int maxLength, boolean visible) {
		final FBView view = getTextView();
		final ZLTextWordCursor cursor = view.getStartCursor();

		if (cursor.isNull()) {
			return null;
		}

		return Bookmark.createBookmark(
			Model.Book,
			view.getModel().getId(),
			cursor,
			maxLength,
			visible
		);
	}

	public TOCTree getCurrentTOCElement() {
		final ZLTextWordCursor cursor = BookTextView.getStartCursor();
		if (Model == null || cursor == null) {
			return null;
		}

		int index = cursor.getParagraphIndex();
		if (cursor.isEndOfParagraph()) {
			++index;
		}
		TOCTree treeToSelect = null;
		for (TOCTree tree : Model.TOCTree) {
			final TOCTree.Reference reference = tree.getReference();
			if (reference == null) {
				continue;
			}
			if (reference.ParagraphIndex > index) {
				break;
			}
			treeToSelect = tree;
		}
		return treeToSelect;
	}

	public void onBookUpdated(Book book) {
		if (Model == null || Model.Book == null || !Model.Book.equals(book)) {
			return;
		}

		final String newEncoding = book.getEncodingNoDetection();
		final String oldEncoding = Model.Book.getEncodingNoDetection();

		Model.Book.updateFrom(book);

		if (newEncoding != null && !newEncoding.equals(oldEncoding)) {
			reloadBook();
		} else {
			ZLTextHyphenator.Instance().load(Model.Book.getLanguage());
			clearTextCaches();
			getViewWidget().repaint();
		}
	}
}
