package ru.ravel.rcriflayouttool.dto

data class ActivitiesForMenu(
	val name: String,
	val reference: String,
	val exitName: List<String>,
	val toActivity: List<String>,
)