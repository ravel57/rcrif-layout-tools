package ru.ravel.rcriflayouttool.dto

import java.io.File

data class CommentRow(
	val name: String,
	val comment: String,
	val file: File
)