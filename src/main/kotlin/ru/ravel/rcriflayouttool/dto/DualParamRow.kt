package ru.ravel.rcriflayouttool.dto

import javafx.beans.property.SimpleStringProperty


data class DualParamRow(
	val firstField: SimpleStringProperty,
	val secondField: SimpleStringProperty,
)