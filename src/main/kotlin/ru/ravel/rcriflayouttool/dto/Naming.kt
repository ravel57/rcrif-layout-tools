package ru.ravel.rcriflayouttool.dto

enum class Naming(val prefix: String) {
	FORM("FM"),
	BUSINESS_RULE("BR"),
	SEGMENTATION_TREE("ST"),
	DATA_SOURCE("DS"),
	DATA_MAPPING("DM"),
	SET_VALUE("SV"),
	PROCEDURE_CALL("PC"),
	DISPATCH("DR"),
	WAIT("WA"),
	PROCEDURE_RETURN("PR"),
	END_PROCEDURE("EP"),
	SEND_EMAIL("SE"),
	SET_PHASE("SP"),
	UNKNOWN(""),
}