/*
 * Copyright 2012 Alessandro Bahgat Shehata
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.mezzdev.suffixtree;

/**
 * The root node can have a lot of values added to it because so many suffix links point to it.
 * The values are never read from here though.
 * This class makes sure we don't accumulate unused values in the root node.
 */
public class RootNode<T> extends Node<T> {
	public RootNode() {
		super(new SubString(""));
	}

	@Override
	protected boolean contains(T value) {
		return true;
	}

	@Override
	protected void addValue(T value) {
		// noop
	}
}
