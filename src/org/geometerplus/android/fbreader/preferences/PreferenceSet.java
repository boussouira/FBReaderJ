/*
 * Copyright (C) 2010-2014 Geometer Plus <contact@geometerplus.com>
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

package org.geometerplus.android.fbreader.preferences;

import java.util.LinkedList;

import android.preference.Preference;

abstract class PreferenceSet<T> {
	private final LinkedList<Preference> myPreferences = new LinkedList<Preference>();

	final void add(Preference preference) {
		myPreferences.add(preference);
	}

	final void update() {
		final T state = detectState();
		for (Preference preference : myPreferences) {
			update(preference, state);
		}
	}

	protected abstract T detectState();
	protected abstract void update(Preference preference, T state);

	static abstract class Enabler extends PreferenceSet<Boolean> {
		protected void update(Preference preference, Boolean state) {
			preference.setEnabled(state);
		}
	}

	static class Reloader extends PreferenceSet<Void> {
		protected Void detectState() {
			return null;
		}

		protected void update(Preference preference, Void state) {
			((ReloadablePreference)preference).reload();
		}
	}
}