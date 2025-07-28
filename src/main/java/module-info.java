module ru.ravel.rcriflayouttool {
	requires javafx.controls;
	requires javafx.fxml;
	requires kotlin.stdlib;
	requires com.fasterxml.jackson.dataformat.xml;
	requires com.fasterxml.jackson.annotation;


	opens ru.ravel.rcriflayouttool to javafx.fxml;
	opens ru.ravel.rcriflayouttool.model.layout to com.fasterxml.jackson.databind, com.fasterxml.jackson.dataformat.xml;
	opens ru.ravel.rcriflayouttool.model.properties to com.fasterxml.jackson.databind, com.fasterxml.jackson.dataformat.xml;
	exports ru.ravel.rcriflayouttool;
}