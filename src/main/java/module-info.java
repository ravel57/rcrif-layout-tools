module ru.ravel.rcriflayouttool {
	requires javafx.controls;
	requires javafx.fxml;
	requires kotlin.stdlib;
	requires com.fasterxml.jackson.dataformat.xml;
	requires com.fasterxml.jackson.annotation;
	requires org.fxmisc.flowless;
	requires org.fxmisc.richtext;
	requires reactfx;
	requires io.github.javadiffutils;
	requires com.fasterxml.jackson.databind;


	opens ru.ravel.rcriflayouttool to javafx.graphics, com.fasterxml.jackson.databind;
	opens ru.ravel.rcriflayouttool.model.layout to com.fasterxml.jackson.databind, com.fasterxml.jackson.dataformat.xml;
	opens ru.ravel.rcriflayouttool.model.procedureproperties to com.fasterxml.jackson.databind, com.fasterxml.jackson.dataformat.xml;
	opens ru.ravel.rcriflayouttool.model.connectorproperties to com.fasterxml.jackson.databind, com.fasterxml.jackson.dataformat.xml;
	opens ru.ravel.rcriflayouttool.model.mappingproperties to com.fasterxml.jackson.databind, com.fasterxml.jackson.dataformat.xml;
	exports ru.ravel.rcriflayouttool;
}