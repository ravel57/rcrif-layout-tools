package ru.ravel.rcriflayouttool.dto

import org.fxmisc.richtext.model.StyleSpans


data class DiffResult(
	val spansL: StyleSpans<Collection<String>>,
	val spansR: StyleSpans<Collection<String>>,
	val nav: List<DiffNav>,
	val deletedLines: List<Int>,
	val insertedLines: List<Int>,
)