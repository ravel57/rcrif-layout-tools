module ru.ravel.rcriflayouttool {
	requires javafx.controls;
	requires javafx.fxml;
	requires kotlin.stdlib;


	opens ru.ravel.rcriflayouttool to javafx.fxml;
	exports ru.ravel.rcriflayouttool;
}