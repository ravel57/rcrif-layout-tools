package ru.ravel.rcriflayouttool.dto

import javafx.beans.property.SimpleStringProperty


data class TripleParamRow(
	val firstField: SimpleStringProperty,
	val secondField: SimpleStringProperty,
	val thirdField: SimpleStringProperty,
)