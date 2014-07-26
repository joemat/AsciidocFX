package com.kodcu.controller;


import com.kodcu.other.Current;
import com.kodcu.other.IOHelper;
import com.kodcu.other.Item;
import com.kodcu.service.AsciiDoctorRenderService;
import com.kodcu.service.DocBookService;
import com.kodcu.service.FileBrowseService;
import com.kodcu.service.FopPdfService;
import com.sun.javafx.application.HostServicesDelegate;
import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import netscape.javascript.JSObject;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.EmbeddedWebApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.xml.sax.SAXException;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;


@Controller
public class AsciiDocController extends TextWebSocketHandler implements Initializable {

    Logger logger = LoggerFactory.getLogger(AsciiDocController.class);

    public TabPane tabPane;
    public WebView previewView;
    public MenuItem openItem;
    public MenuItem newItem;
    public MenuItem saveItem;
    public SplitPane splitPane;
    public Menu recentMenu;
    public TreeView<Item> treeView;
    public Button splitHideButton;
    public Button WorkingDirButton;
    public Button convertDocbook;
    public MenuBar menubar;
    public HBox windowHBox;
    public Button generatePdf;
    public ProgressIndicator indikator;

    @Autowired
    private TablePopupController tablePopupController;

    @Autowired
    private AsciiDoctorRenderService docConverter;

    @Autowired
    private DocBookService docBookController;

    @Autowired
    private FopPdfService fopServiceRunner;

    @Autowired
    private Current current;

    @Autowired
    private FileBrowseService fileBrowser;

    private Stage stage;
    private WebEngine previewEngine;
    private StringProperty lastRendered = new SimpleStringProperty();
    private List<WebSocketSession> sessionList = new ArrayList<>();
    private Scene scene;
    private AnchorPane tableAnchor;
    private Stage tableStage;

    private Clipboard clipboard = Clipboard.getSystemClipboard();
    private Optional<Path> initialDirectory = Optional.empty();
    private Set<Path> recentFiles = new HashSet<>();

    private String waitForGetValue;
    private String waitForSetValue;

    private AnchorPane configAnchor;
    private Stage configStage;

    @Autowired
    private EmbeddedWebApplicationContext server;

    private int tomcatPort = 8080;
    private HostServicesDelegate hostServices;
    private double sceneXOffset;
    private double sceneYOffset;


    @FXML
    private void createTable(ActionEvent event) throws IOException {
        tableStage.show();
    }

    @FXML
    private void convertDocbook(ActionEvent event) {

        Path currentPath = initialDirectory.map(path -> Files.isDirectory(path) ? path : path.getParent()).get();
        docBookController.generateDocbook(previewEngine, currentPath, true);

    }

    @FXML
    private void openConfig(ActionEvent event) {
        configStage.show();
    }


    @FXML
    private void fullScreen(ActionEvent event) {

        getStage().setFullScreen(!getStage().isFullScreen());
    }

    @FXML
    private void directoryView(ActionEvent event) {
        splitPane.setDividerPositions(0.1610294117647059, 0.5823529411764706);
    }


    @FXML
    private void generatePdf(ActionEvent event) throws IOException, SAXException {

        Path currentPath = initialDirectory.map(path -> Files.isDirectory(path) ? path : path.getParent()).get();

        docBookController.generateDocbook(previewEngine, currentPath, false);

        Task<Boolean> task = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                fopServiceRunner.generate(currentPath, Paths.get("").toAbsolutePath());
                return null;
            }
        };
        new Thread(task).start();

    }


    @FXML
    private void maximize(Event event) {

        // Get current screen of the stage
        Rectangle2D rectangle2D = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
        ObservableList<Screen> screens = Screen.getScreensForRectangle(rectangle2D);

        // Change stage properties
        Rectangle2D bounds = screens.get(0).getVisualBounds();

        if (bounds.getHeight() == stage.getHeight() && bounds.getWidth() == stage.getWidth()) {
            stage.setX(50);
            stage.setY(50);
            stage.setWidth(bounds.getWidth() * 0.8);
            stage.setHeight(bounds.getHeight() * 0.8);
        } else {
            stage.setX(bounds.getMinX());
            stage.setY(bounds.getMinY());
            stage.setWidth(bounds.getWidth());
            stage.setHeight(bounds.getHeight());
        }
    }

    @FXML
    private void minimize(ActionEvent event) {
        getStage().setIconified(true);
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        tomcatPort = server.getEmbeddedServletContainer().getPort();

        waitForGetValue = IOHelper.convert(AsciiDocController.class.getResourceAsStream("/waitForGetValue.js"));
        waitForSetValue = IOHelper.convert(AsciiDocController.class.getResourceAsStream("/waitForSetValue.js"));

        lastRendered.addListener((observableValue, old, nev) -> {
            sessionList.stream().filter(e -> e.isOpen()).forEach(e -> {
                try {
                    e.sendMessage(new TextMessage(nev));
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            });
        });


        previewEngine = previewView.getEngine();
        previewEngine.load(String.format("http://localhost:%d/index.html", tomcatPort));

        previewEngine.getLoadWorker().stateProperty().addListener((observableValue1, state, state2) -> {
            if (state2 == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) previewEngine.executeScript("window");
                window.setMember("app", this);
            }
        });

        previewEngine.getLoadWorker().exceptionProperty().addListener((ov, t, t1) -> {
            t1.printStackTrace();
        });


        /// Treeview
//
        fileBrowser.browse(treeView, this, System.getProperty("user.home"));

        //

        AwesomeDude.setIcon(WorkingDirButton, AwesomeIcon.FOLDER_OPEN_ALT);
        AwesomeDude.setIcon(splitHideButton, AwesomeIcon.CHEVRON_LEFT);
        AwesomeDude.setIcon(convertDocbook, AwesomeIcon.FILE_TEXT_ALT);
        AwesomeDude.setIcon(generatePdf, AwesomeIcon.FILE_PDF_ALT);

        //

        menubar.setOnMouseClicked(event -> {
            if (event.getClickCount() > 1)
                maximize(event);
        });

        menubar.setOnMousePressed(event -> {
            sceneXOffset = event.getSceneX();
            sceneYOffset = event.getSceneY();
        });
        menubar.setOnMouseDragged(event -> {
            getStage().setX(event.getScreenX() - sceneXOffset);
            getStage().setY(event.getScreenY() - sceneYOffset);
        });

        //


    }

    public void externalBrowse() {

        hostServices.showDocument(String.format("http://localhost:%d/index.html", tomcatPort));
    }

    @FXML
    public void changeWorkingDir(ActionEvent actionEvent) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        initialDirectory.ifPresent(path -> {
            if (Files.isDirectory(path))
                directoryChooser.setInitialDirectory(path.toFile());
            else
                directoryChooser.setInitialDirectory(path.getParent().toFile());
        });
        directoryChooser.setTitle("Select Working Directory");
        File selectedDir = directoryChooser.showDialog(null);
        if (Objects.nonNull(selectedDir)) {
            initialDirectory = Optional.of(selectedDir.toPath());
            fileBrowser.browse(treeView, this, selectedDir.toString());
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessionList.add(session);
        String value = lastRendered.getValue();
        if (Objects.nonNull(value))
            session.sendMessage(new TextMessage(value));

    }

    @FXML
    private void closeApp(ActionEvent event) {
        Platform.exit();
        System.exit(0);
    }

    @FXML
    private void openDoc(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Asciidoc", "*.asciidoc", "*.adoc", "*.asc", "*.ad", "*.txt"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All", "*.*"));
        initialDirectory.ifPresent(e -> {
            if (Files.isDirectory(e))
                fileChooser.setInitialDirectory(e.toFile());
            else
                fileChooser.setInitialDirectory(e.getParent().toFile());
        });
        List<File> chosenFiles = fileChooser.showOpenMultipleDialog(stage);
        if (chosenFiles != null) {
            initialDirectory = Optional.of(chosenFiles.get(0).toPath());
            chosenFiles.stream().map(e -> e.toPath()).forEach(this::addTab);
            recentFiles.addAll(chosenFiles.stream().map(e -> e.toPath()).collect(Collectors.toList()));
        }

    }

    @FXML
    private void recentFileList(Event event) {
        List<MenuItem> menuItems = recentFiles.stream().filter(path -> !Files.isDirectory(path)).map(path -> {
            MenuItem menuItem = new MenuItem();
            menuItem.setText(path.toAbsolutePath().toString());
            menuItem.setOnAction(actionEvent -> {
                addTab(path);
            });
            return menuItem;
        }).limit(20).collect(Collectors.toList());

        recentMenu.getItems().clear();
        recentMenu.getItems().addAll(menuItems);

    }

    @FXML
    private void newDoc(ActionEvent event) {

        WebView webView = createWebView();
        AnchorPane anchorPane = new AnchorPane();
        anchorPane.getChildren().add(webView);
        fitToParent(webView);
        Tab tab = createTab();
        tab.setContent(anchorPane);
        tab.selectedProperty().addListener((observableValue, before, after) -> {
            if (after) {
                current.putTab(tab, current.getNewTabPaths().get(tab), webView);
                WebEngine webEngine = webView.getEngine();

                if (webEngine.getLoadWorker().getState() == Worker.State.SUCCEEDED)
                    webEngine.executeScript(waitForGetValue);
            }
        });
        ((Label) tab.getGraphic()).setText("new *");
        tabPane.getTabs().add(tab);

        current.putTab(tab, null, webView);
    }


    public void addTab(Path path) {
        AnchorPane anchorPane = new AnchorPane();
        WebView webView = createWebView();
        WebEngine webEngine = webView.getEngine();
        webEngine.getLoadWorker().stateProperty().addListener((observableValue1, state, state2) -> {
            if (state2 == Worker.State.SUCCEEDED) {
                webEngine.executeScript(String.format(waitForSetValue, IOHelper.readFile(path)));
            }
        });

        anchorPane.getChildren().add(webView);

        fitToParent(webView);

        Tab tab = createTab();
        ((Label) tab.getGraphic()).setText(path.getFileName().toString());
        tab.setContent(anchorPane);

        tab.selectedProperty().addListener((observableValue, before, after) -> {
            if (after) {
                current.putTab(tab, path, webView);
                webEngine.executeScript(waitForGetValue);

            }
        });

        current.putTab(tab, path, webView);
        tabPane.getTabs().add(tab);

        Tab lastTab = tabPane.getTabs().get(tabPane.getTabs().size() - 1);
        tabPane.getSelectionModel().select(lastTab);

    }

    @FXML
    public void hideLeftSplit(ActionEvent event) {
        splitPane.setDividerPositions(0, 0.5);
    }

    private Tab createTab() {
        Tab tab = new Tab();

        MenuItem menuItem0 = new MenuItem("Close All Tabs");
        menuItem0.setOnAction(actionEvent -> {
            tabPane.getTabs().clear();
        });
        MenuItem menuItem1 = new MenuItem("Close All Other Tabs");
        menuItem1.setOnAction(actionEvent -> {
            List<Tab> blackList = new ArrayList<>();
            blackList.addAll(tabPane.getTabs());
            blackList.remove(tab);
            tabPane.getTabs().removeAll(blackList);
        });

        ContextMenu contextMenu = new ContextMenu();
        contextMenu.getItems().addAll(menuItem0, menuItem1);

        tab.contextMenuProperty().setValue(contextMenu);
        Label label = new Label();

        label.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getClickCount() > 1) {
                if (splitPane.getDividerPositions()[0] > 0.1)
                    splitPane.setDividerPositions(0, 1);
                else
                    splitPane.setDividerPositions(0.1610294117647059, 0.5823529411764706);
            }
        });

        tab.setGraphic(label);


        return tab;
    }


    private WebView createWebView() {

        WebView webView = new WebView();

        WebEngine webEngine = webView.getEngine();
        JSObject window = (JSObject) webEngine.executeScript("window");
        window.setMember("app", this);
        webEngine.load(String.format("http://localhost:%d/editor.html", tomcatPort));

        return webView;
    }

    public void onscroll(Object pos, Object max) {
        if (Objects.isNull(pos) || Objects.isNull(max))
            return;

        Number position = (Number) pos; // current scroll position for editor
        Number maximum = (Number) max; // max scroll position for editor

        double ratio = (position.doubleValue() * 100) / maximum.doubleValue();
        Integer browserMaxScroll = (Integer) previewEngine.executeScript("document.documentElement.scrollHeight - document.documentElement.clientHeight;");
        double browserScrollOffset = (Double.valueOf(browserMaxScroll) * ratio) / 100.0;
        previewEngine.executeScript(String.format("window.scrollTo(0, %f )", browserScrollOffset));

    }

    @RequestMapping(value = {"**.asciidoc", "**.asc", "**.txt", "**.ad", "**.adoc"}, method = RequestMethod.GET)
    @ResponseBody
    public DeferredResult<String> asciidoc(HttpServletRequest request) {

        DeferredResult<String> deferredResult = new DeferredResult<String>();

        String uri = request.getRequestURI();

        if (uri.startsWith("/"))
            uri = uri.substring(1);

        if (Objects.nonNull(current.currentPath())) {
            Path ascFile = current.currentParentRoot().resolve(uri);

            Platform.runLater(() -> {
                this.addTab(ascFile);
            });

            deferredResult.setResult("OK");
        }

        return deferredResult;
    }

    @RequestMapping(value = {"/**/{extension:(?:\\w|\\W)+\\.(?:jpg|bmp|gif|jpeg|png|webp)$}"}, method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<byte[]> images(HttpServletRequest request, @PathVariable("extension") String extension) throws IOException {


        Enumeration<String> headerNames = request.getHeaderNames();
        String uri = request.getRequestURI();
        byte[] temp = new byte[]{};
        if (uri.startsWith("/"))
            uri = uri.substring(1);

        if (Objects.nonNull(current.currentPath())) {
            Path imageFile = current.currentParentRoot().resolve(uri);
            FileInputStream fileInputStream = new FileInputStream(imageFile.toFile());
            temp = IOUtils.toByteArray(fileInputStream);
            IOUtils.closeQuietly(fileInputStream);

        }

        return new ResponseEntity<>(temp, HttpStatus.OK);
    }

    public void textListener(ObservableValue observableValue, String old, String nev) {
        try {
            Platform.runLater(() -> {

                String rendered = docConverter.asciidocToHtml(previewEngine, nev);

                lastRendered.setValue(rendered);

                Label label = (Label) current.getCurrentTab().getGraphic();
                if (!label.getText().contains(" *"))
                    label.setText(label.getText() + " *");
            });
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }


    public void cutCopy(String data) {
        ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putString(data);
        clipboard.setContent(clipboardContent);
    }

    public String paste() {
        return clipboard.getString();
    }

    @FXML
    public void saveDoc() {
        Path currentPath = current.currentPath();
        if (currentPath == null) {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Asciidoc", "*.asciidoc", "*.adoc", "*.asc", "*.ad", "*.txt"));
            File file = chooser.showSaveDialog(null);
            if (file == null)
                return;
            IOHelper.writeToFile(file, (String) current.currentEngine().executeScript("editor.getValue();"), TRUNCATE_EXISTING, CREATE);
            current.putTab(current.getCurrentTab(), file.toPath(), current.currentView());
            current.setCurrentTabText(file.toPath().getFileName().toString());
            recentFiles.add(file.toPath());
        } else {
            IOHelper.writeToFile(currentPath.toFile(), (String) current.currentEngine().executeScript("editor.getValue();"), TRUNCATE_EXISTING, CREATE);
        }

        Label label = (Label) current.getCurrentTab().getGraphic();
        label.setText(label.getText().replace(" *", ""));
    }

    private void fitToParent(Node node) {
        AnchorPane.setTopAnchor(node, 0.0);
        AnchorPane.setBottomAnchor(node, 0.0);
        AnchorPane.setLeftAnchor(node, 0.0);
        AnchorPane.setRightAnchor(node, 0.0);
    }

    public ProgressIndicator getIndikator() {
        return indikator;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public Stage getStage() {
        return stage;
    }

    public void setScene(Scene scene) {
        this.scene = scene;
    }

    public Scene getScene() {
        return scene;
    }

    public void setTableAnchor(AnchorPane tableAnchor) {
        this.tableAnchor = tableAnchor;
    }

    public AnchorPane getTableAnchor() {
        return tableAnchor;
    }

    public void setTableStage(Stage tableStage) {
        this.tableStage = tableStage;
    }

    public Stage getTableStage() {
        return tableStage;
    }

    public void setConfigAnchor(AnchorPane configAnchor) {
        this.configAnchor = configAnchor;
    }

    public AnchorPane getConfigAnchor() {
        return configAnchor;
    }

    public void setConfigStage(Stage configStage) {
        this.configStage = configStage;
    }

    public Stage getConfigStage() {
        return configStage;
    }


    public SplitPane getSplitPane() {
        return splitPane;
    }

    public TreeView<Item> getTreeView() {
        return treeView;
    }

    public void setHostServices(HostServicesDelegate hostServices) {
        this.hostServices = hostServices;
    }

    public HostServicesDelegate getHostServices() {
        return hostServices;
    }

    public Optional<Path> getInitialDirectory() {
        return initialDirectory;
    }


}